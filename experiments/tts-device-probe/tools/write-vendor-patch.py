"""Regenerate the adopted-source patch from pinned, hash-checked local archives.

No Git, network, or upstream scripts. Does not modify adopted or original sources.
"""
import difflib
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / "vendor"


def main():
    manifest = json.loads((VENDOR / "UPSTREAM_FILES.json").read_text(encoding="utf-8"))
    origins = {
        "qwen3-tts": ROOT / "vendor-downloads" / ("qwen3-tts.cpp-" + manifest["engine_commit"]),
        "ggml": ROOT / "vendor-downloads" / ("ggml-" + manifest["ggml_commit"]),
    }
    patch = []
    changed = []
    for entry in manifest["files"]:
        relative = Path(entry["path"])
        original = origins[relative.parts[0]].joinpath(*relative.parts[1:]).read_bytes()
        if hashlib.sha256(original).hexdigest() != entry["upstream_sha256"]:
            raise SystemExit("Original source hash mismatch: " + entry["path"])
        adopted = (VENDOR / relative).read_bytes()
        if original != adopted:
            patch.extend(difflib.unified_diff(
                original.decode("utf-8").splitlines(keepends=True),
                adopted.decode("utf-8").splitlines(keepends=True),
                fromfile="a/vendor/" + entry["path"], tofile="b/vendor/" + entry["path"],
            ))
            changed.append({"path": entry["path"], "sha256": hashlib.sha256(adopted).hexdigest()})
    data = "".join(patch).encode("utf-8")
    (VENDOR / "LOCAL_CHANGES.patch").write_bytes(data)
    print(json.dumps({"adopted_files": len(manifest["files"]), "changed": changed,
                      "patch_sha256": hashlib.sha256(data).hexdigest()}, indent=2))


if __name__ == "__main__":
    main()
