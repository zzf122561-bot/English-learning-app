"""Compile the affected Android translation units with buffer reuse disabled.

Reuses the actual CMake compile database without modifying its build tree or AAR.
This is syntax/type checking, not native execution or a speed comparison.
"""
import ctypes
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {"transformer_generate.cpp", "transformer_runtime_code_pred.cpp", "probe_jni.cpp"}


def split_windows(command):
    shell = ctypes.WinDLL("shell32", use_last_error=True)
    shell.CommandLineToArgvW.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_int)]
    shell.CommandLineToArgvW.restype = ctypes.POINTER(ctypes.c_wchar_p)
    count = ctypes.c_int()
    pointer = shell.CommandLineToArgvW(command, ctypes.byref(count))
    if not pointer:
        raise ctypes.WinError()
    try:
        return [pointer[i] for i in range(count.value)]
    finally:
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.LocalFree.argtypes = [ctypes.c_void_p]
        kernel.LocalFree(pointer)


def main():
    database = Path(sys.argv[1]).resolve()
    if not database.is_relative_to(ROOT / ".cxx"):
        raise ValueError("Expected this Library's CMake compile database")
    commands = json.loads(database.read_text(encoding="utf-8"))
    rows = []
    for entry in commands:
        source = Path(entry["file"]).resolve()
        if source.name not in TARGETS:
            continue
        if not source.is_relative_to(ROOT):
            raise ValueError("Source escaped module")
        args = split_windows(entry["command"])
        if Path(args[0]).resolve() != Path("D:/CodexData/android-toolchain/android-sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe").resolve():
            raise ValueError("Expected approved fixed NDK compiler")
        if "-DPROBE_REUSE_CODE_BUFFERS=1" not in args:
            raise ValueError("Expected the optimized build's explicit flag")
        args[args.index("-DPROBE_REUSE_CODE_BUFFERS=1")] = "-DPROBE_REUSE_CODE_BUFFERS=0"
        output = args.index("-o")
        del args[output:output + 2]
        args.remove("-c")
        args.append("-fsyntax-only")
        result = subprocess.run(args, cwd=entry["directory"], capture_output=True, text=True, shell=False)
        rows.append({"source": str(source.relative_to(ROOT)), "exit_code": result.returncode,
                     "diagnostics": result.stdout + result.stderr})
        print(source.name, result.returncode, flush=True)
    if len(rows) != 3:
        raise ValueError("Expected exactly the three affected translation units")
    destination = ROOT / ".local/cpu-buffer-fallback.json"
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps({"mode": "Android arm64 syntax-only; buffer reuse OFF", "results": rows}, indent=2), encoding="utf-8")
    if any(row["exit_code"] for row in rows):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
