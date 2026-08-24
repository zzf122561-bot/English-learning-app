#!/usr/bin/env python3
"""Read-only MDX/MDD header and digest auditor used by milestone 1."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from pathlib import Path


ATTRIBUTE_RE = re.compile(r"([A-Za-z][A-Za-z0-9_-]*)\s*=\s*(['\"])(.*?)\2")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def decode_header(raw: bytes) -> str:
    candidates = ("utf-16le", "utf-16be", "utf-8")
    for encoding in candidates:
        try:
            value = raw.decode(encoding).lstrip("\ufeff\x00")
        except UnicodeDecodeError:
            continue
        if "GeneratedByEngineVersion" in value or "RequiredEngineVersion" in value:
            return value.rstrip("\x00")
    raise ValueError("MDict header encoding is not recognized")


def inspect(path: Path) -> dict[str, object]:
    size_before = path.stat().st_size
    with path.open("rb") as source:
        length_bytes = source.read(4)
        if len(length_bytes) != 4:
            raise ValueError("File is too short for an MDict header")
        header_length = struct.unpack(">I", length_bytes)[0]
        if header_length <= 0 or header_length > 16 * 1024 * 1024:
            raise ValueError(f"Implausible MDict header length: {header_length}")
        header = decode_header(source.read(header_length))
        checksum = source.read(4).hex()

    attributes = {match.group(1): match.group(3) for match in ATTRIBUTE_RE.finditer(header)}
    digest = sha256(path)
    size_after = path.stat().st_size
    if size_before != size_after:
        raise RuntimeError("Dictionary size changed during read-only audit")

    encrypted = attributes.get("Encrypted", "0")
    return {
        "path": str(path.resolve()),
        "kind": path.suffix.lower().lstrip("."),
        "size": size_after,
        "sha256": digest,
        "header_length": header_length,
        "header_checksum_bytes": checksum,
        "generated_by_engine_version": attributes.get("GeneratedByEngineVersion"),
        "required_engine_version": attributes.get("RequiredEngineVersion"),
        "encoding": attributes.get("Encoding"),
        "encrypted": encrypted,
        "ripemd128_key_info_expected": encrypted in {"2", "3", "Yes", "yes"},
        "title": attributes.get("Title"),
        "description_present": bool(attributes.get("Description")),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+")
    args = parser.parse_args()
    reports = []
    for raw_path in args.paths:
        # Some Windows launchers preserve the protective quote characters in argv.
        path = Path(raw_path.strip('"'))
        if path.suffix.lower() not in {".mdx", ".mdd"}:
            parser.error(f"unsupported extension: {path}")
        reports.append(inspect(path))
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
