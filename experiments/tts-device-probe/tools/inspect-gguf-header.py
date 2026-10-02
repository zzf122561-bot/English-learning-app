"""Inspect a bounded local GGUF v3 prefix without loading model tensor data.

No network, third-party packages, or inference. Large metadata arrays are counted,
not retained. The JSON is structural evidence only, never a full-file hash check.
"""

import argparse
import collections
import hashlib
import json
import struct
from pathlib import Path


SCALARS = {0: "B", 1: "b", 2: "H", 3: "h", 4: "I", 5: "i",
           6: "f", 7: "?", 10: "Q", 11: "q", 12: "d"}


class HeaderReader:
    def __init__(self, data):
        self.data = data
        self.position = 0

    def take(self, size):
        if size < 0 or self.position + size > len(self.data):
            raise ValueError("Incomplete or invalid GGUF prefix")
        start = self.position
        self.position += size
        return self.data[start:self.position]

    def scalar(self, fmt):
        return struct.unpack("<" + fmt, self.take(struct.calcsize("<" + fmt)))[0]

    def string(self, retain=True):
        length = self.scalar("Q")
        data = self.take(length)
        return data.decode("utf-8") if retain else None

    def value(self, kind, retain=True):
        if kind in SCALARS:
            value = self.scalar(SCALARS[kind])
            return value if retain else None
        if kind == 8:
            return self.string(retain)
        if kind != 9:
            raise ValueError(f"Unknown metadata type {kind}")
        element_kind, count = self.scalar("I"), self.scalar("Q")
        if element_kind == 9 or count > len(self.data):
            raise ValueError("Invalid or excessive metadata array")
        keep = retain and count <= 100
        values = []
        for _ in range(count):
            value = self.value(element_kind, keep)
            if keep:
                values.append(value)
        return values if keep else {"element_type": element_kind, "count": count}


def inspect(path, file_size):
    if path.stat().st_size > 16 * 1024 * 1024:
        raise ValueError("This tool accepts bounded prefixes of at most 16 MiB")
    data = path.read_bytes()
    reader = HeaderReader(data)
    if reader.take(4) != b"GGUF" or reader.scalar("I") != 3:
        raise ValueError("Expected little-endian GGUF v3")
    tensor_count, kv_count = reader.scalar("Q"), reader.scalar("Q")
    if tensor_count > 100_000 or kv_count > 100_000:
        raise ValueError("Excessive header counts")
    metadata, metadata_types = {}, {}
    for _ in range(kv_count):
        key, kind = reader.string(), reader.scalar("I")
        if key in metadata:
            raise ValueError(f"Duplicate metadata key: {key}")
        metadata[key] = reader.value(kind)
        metadata_types[key] = kind
    tensors = {}
    for _ in range(tensor_count):
        name, dimensions = reader.string(), reader.scalar("I")
        if name in tensors or not 1 <= dimensions <= 4:
            raise ValueError("Duplicate tensor or invalid dimension count")
        shape = [reader.scalar("Q") for _ in range(dimensions)]
        kind, offset = reader.scalar("I"), reader.scalar("Q")
        if any(d <= 0 for d in shape):
            raise ValueError("Invalid tensor dimensions")
        tensors[name] = {"shape": shape, "type": kind, "offset": offset}
    alignment = metadata.get("general.alignment", 32)
    if not isinstance(alignment, int) or alignment < 1 or alignment & (alignment - 1):
        raise ValueError("Invalid alignment")
    data_offset = (reader.position + alignment - 1) // alignment * alignment
    if data_offset > len(data) or any(data_offset + t["offset"] >= file_size for t in tensors.values()):
        raise ValueError("Incomplete header or tensor offset outside reported full file")
    return {
        "evidence_scope": "GGUF prefix only; no full hash, load, or inference validation",
        "prefix_file": path.name, "prefix_bytes": len(data),
        "prefix_sha256": hashlib.sha256(data).hexdigest(),
        "reported_full_file_bytes": file_size, "gguf_version": 3,
        "tensor_count": tensor_count, "metadata_count": kv_count,
        "header_end": reader.position, "tensor_data_offset": data_offset,
        "tensor_type_counts": dict(sorted(collections.Counter(t["type"] for t in tensors.values()).items())),
        "metadata": metadata, "metadata_types": metadata_types, "tensors": tensors,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("prefix", type=Path)
    parser.add_argument("--full-file-bytes", required=True, type=int)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    result = inspect(args.prefix, args.full_file_bytes)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in {"tensors", "metadata_types"}}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
