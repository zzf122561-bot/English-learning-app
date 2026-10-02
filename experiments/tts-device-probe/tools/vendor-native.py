"""Copy the audited, pinned CPU source subset. Offline; never runs upstream tools.

Run only for initial import into an empty vendor directory. Local modifications
are preserved: existing destinations cause failure rather than being overwritten.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = "16bb5afcd06311031c72a8488f8d59660dc2fb46"
GGML = "af97976c7810cdabb1863172f31c432dab767de7"
SOURCES = [
    "text_tokenizer.cpp", "gguf_loader.cpp", "tokenizer_unicode.cpp", "tokenizer_unicode_data.cpp",
    "tts_transformer.cpp", "coreml_code_predictor_stub.cpp", "qwen3_tts.cpp",
    "audio_tokenizer_encoder.cpp", "speech_tokenizer_encoder.cpp",
    "transformer/transformer_cache.cpp", "transformer/transformer_debug.cpp",
    "transformer/transformer_embeddings.cpp", "transformer/transformer_graph_code_pred.cpp",
    "transformer/transformer_graph_talker.cpp", "transformer/transformer_loader.cpp",
    "transformer/transformer_loader_metadata.cpp", "transformer/transformer_loader_tensors.cpp",
    "transformer/transformer_runtime.cpp", "transformer/transformer_runtime_code_pred.cpp",
    "transformer/transformer_generate.cpp", "encoder/encoder_frontend.cpp",
    "encoder/encoder_graph.cpp", "encoder/encoder_loader.cpp", "encoder/encoder_runtime.cpp",
    "decoder/decoder_cache.cpp", "decoder/decoder_graph.cpp", "decoder/decoder_layers.cpp",
    "decoder/decoder_loader.cpp", "decoder/decoder_runtime.cpp", "common/audio_io.cpp",
    "common/icl_prompt_io.cpp", "common/speaker_embedding_io.cpp",
    "pipeline/pipeline_models.cpp", "pipeline/pipeline_runtime.cpp", "pipeline/pipeline_synthesize.cpp",
]


def main():
    destination = ROOT / "vendor"
    if destination.exists():
        raise SystemExit("vendor already exists; refusing to overwrite adopted sources")
    engine = ROOT / "vendor-downloads" / ("qwen3-tts.cpp-" + ENGINE)
    ggml = ROOT / "vendor-downloads" / ("ggml-" + GGML)
    groups = {"qwen3-tts": (engine, [engine / "LICENSE"] +
              [engine / "src" / name for name in SOURCES] + list((engine / "src").rglob("*.h"))),
              "ggml": (ggml, [ggml / "LICENSE", ggml / "CMakeLists.txt"] +
              list((ggml / "include").glob("*.h")) +
              [ggml / "cmake" / name for name in ["common.cmake", "ggml-config.cmake.in"]] +
              [p for p in (ggml / "src").iterdir() if p.is_file() and p.suffix in {".cpp", ".c", ".h", ".txt"}] +
              [p for p in (ggml / "src/ggml-cpu").iterdir() if p.is_file() and p.suffix in {".cpp", ".c", ".h", ".txt"}] +
              [p for part in ["amx", "arch/arm", "arch/x86"] for p in (ggml / "src/ggml-cpu" / part).rglob("*") if p.is_file()])}
    entries = []
    for component, (base, paths) in groups.items():
        for source in sorted(set(paths)):
            relative = source.relative_to(base)
            target = destination / component / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            data = source.read_bytes()
            target.write_bytes(data)
            entries.append({"path": target.relative_to(destination).as_posix(),
                            "upstream_sha256": hashlib.sha256(data).hexdigest()})
    (destination / "UPSTREAM_FILES.json").write_text(json.dumps({
        "engine_commit": ENGINE, "ggml_commit": GGML, "files": entries,
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(entries)} pinned source/license files; no upstream scripts executed")


if __name__ == "__main__":
    main()
