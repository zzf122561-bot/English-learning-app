"""Inspect the probe AAR's actual ELF bytes and packaged notices, offline.

This is a packaging/build check. It does not run ARM code or validate an APK.
"""
import hashlib
import io
import json
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def elf_info(data):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected ELF64 little-endian")
    machine = struct.unpack_from("<H", data, 18)[0]
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phsize, phcount = struct.unpack_from("<HH", data, 54)
    loads, relro = [], []
    for index in range(phcount):
        kind, flags, offset, address, _, file_size, memory_size, align = struct.unpack_from(
            "<IIQQQQQQ", data, phoff + index * phsize)
        if kind == 1:
            loads.append({"offset": offset, "address": address, "alignment": align,
                          "memory_bytes": memory_size, "flags": flags,
                          "congruent_16k": (address - offset) % 16384 == 0})
        if kind == 0x6474E552:
            relro.append({"address": address, "memory_bytes": memory_size,
                          "end_aligned_16k": (address + memory_size) % 16384 == 0,
                          "protected_start_16k": address // 16384 * 16384,
                          "protected_end_16k": (address + memory_size + 16383) // 16384 * 16384})
    # Bionic rounds RELRO to the runtime page size; the raw end need not be
    # a multiple of 16K. Verify that this rounding does not protect another
    # writable LOAD. SOURCE_MANIFEST.md records the exact AOSP reference.
    for region in relro:
        region["no_other_writable_load_overlap"] = all(
            segment["address"] == region["address"] or not segment["flags"] & 2 or
            segment["address"] >= region["protected_end_16k"] or
            segment["address"] + segment["memory_bytes"] <= region["protected_start_16k"]
            for segment in loads)
    return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "machine": machine, "loads": loads, "relro": relro,
            "passed": machine == 183 and bool(loads) and bool(relro)
                      and all(p["alignment"] >= 16384 and p["congruent_16k"] for p in loads)
                      and all(p["no_other_writable_load_overlap"] for p in relro)}


def main():
    aar = ROOT / "build/outputs/aar/runtime-release.aar"
    destination = ROOT / ".local/artifact-check"
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(aar) as archive:
        libraries = sorted(name for name in archive.namelist() if name.endswith(".so"))
        expected = ["jni/arm64-v8a/libc++_shared.so", "jni/arm64-v8a/libmodel_probe_qwen.so"]
        if libraries != expected:
            raise ValueError("Unexpected native package contents: " + repr(libraries))
        results = {}
        for name in libraries:
            data = archive.read(name)
            results[name] = elf_info(data)
            (destination / Path(name).name).write_bytes(data)
        with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as classes:
            expected_notices = ["META-INF/model-probe/" + name for name in
                                ["NOTICE.txt", "LICENSE-qwen3-tts.txt", "LICENSE-ggml.txt", "NOTICE-llvm.txt"]]
            for name in expected_notices:
                if classes.read(name) != (ROOT / "src/main/resources" / name).read_bytes():
                    raise ValueError("Packaged notice mismatch: " + name)
        (destination / "AndroidManifest.xml").write_bytes(archive.read("AndroidManifest.xml"))
    result = {"aar": str(aar), "aar_bytes": aar.stat().st_size,
              "aar_sha256": hashlib.sha256(aar.read_bytes()).hexdigest(),
              "libraries": results, "packaged_notices": expected_notices,
              "passed": all(item["passed"] for item in results.values()),
              "runtime_or_apk_validation": False}
    (destination / "inspection.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    if not result["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
