"""Compare inspected Q4 headers with the pinned 1.7B CustomVoice source schema.

Input: vendor-downloads produced during the source audit. This is a static
compatibility check, not a native model load or an audio correctness test.
"""

import json
import re
from pathlib import Path


def main():
    audit = Path(__file__).resolve().parents[1] / "vendor-downloads"
    talker = json.loads((audit / "talker-header.json").read_text(encoding="utf-8"))
    codec = json.loads((audit / "tokenizer-header.json").read_text(encoding="utf-8"))
    config = json.loads((audit / "Qwen3-TTS-12Hz-1.7B-CustomVoice-config.json").read_text(encoding="utf-8"))
    m, t = talker["metadata"], talker["tensors"]
    expected = {
        "talker.text_embd.weight": [2048, 151936],
        "talker.text_proj.fc1.weight": [2048, 2048],
        "talker.text_proj.fc1.bias": [2048],
        "talker.text_proj.fc2.weight": [2048, 2048],
        "talker.text_proj.fc2.bias": [2048],
        "talker.codec_embd.weight": [2048, 3072],
        "talker.codec_head.weight": [2048, 3072],
        "talker.output_norm.weight": [2048],
        "code_pred.mtp_proj.weight": [2048, 1024],
        "code_pred.mtp_proj.bias": [1024],
        "code_pred.output_norm.weight": [1024],
    }
    for prefix, layers, hidden, intermediate in [("talker", 28, 2048, 6144), ("code_pred", 5, 1024, 3072)]:
        suffixes = {
            "attn_norm.weight": [hidden], "attn_q_norm.weight": [128],
            "attn_k_norm.weight": [128], "attn_q.weight": [hidden, 2048],
            "attn_k.weight": [hidden, 1024], "attn_v.weight": [hidden, 1024],
            "attn_output.weight": [2048, hidden], "ffn_norm.weight": [hidden],
            "ffn_gate.weight": [hidden, intermediate],
            "ffn_up.weight": [hidden, intermediate], "ffn_down.weight": [intermediate, hidden],
        }
        for layer in range(layers):
            expected.update({f"{prefix}.blk.{layer}.{name}": shape for name, shape in suffixes.items()})
    for codebook in range(15):
        expected[f"code_pred.codec_embd.{codebook}.weight"] = [2048, 2048]
        expected[f"code_pred.lm_head.{codebook}.weight"] = [1024, 2048]
    errors = []
    for name, shape in expected.items():
        if t.get(name, {}).get("shape") != shape:
            errors.append(f"Tensor mismatch: {name}, expected {shape}, got {t.get(name)}")
    if set(t) != set(expected):
        errors.append("Unexpected talker tensor-name set")
    speakers = dict(zip(m["qwen3-tts.codec.speaker_names"], m["qwen3-tts.codec.speaker_ids"], strict=True))
    languages = dict(zip(m["qwen3-tts.codec.language_names"], m["qwen3-tts.codec.language_ids"], strict=True))
    # decoder_loader.cpp converts these convolution weights to F16. Its reader
    # accepts unchanged F16 or F32/BF16 -> F16, not Q4_K -> F16.
    runtime_f16 = {
        name: tensor for name, tensor in codec["tensors"].items()
        if name in {"tok_dec.pre_conv.weight", "tok_dec.dec.0.conv.weight", "tok_dec.dec.6.conv.weight"}
        or re.fullmatch(r"tok_dec\.upsample\.\d+\.dwconv\.weight", name)
        or re.fullmatch(r"tok_dec\.dec\.\d+\.res\.\d+\.conv[12]\.weight", name)
    }
    checks = {
        "customvoice_1b7": m["qwen3-tts.model_type"] == config["tts_model_type"] == "custom_voice" and m["qwen3-tts.model_size"] == config["tts_model_size"] == "1b7",
        "all_nine_speaker_ids_match_official_config": speakers == config["talker_config"]["spk_id"],
        "english_id": languages["english"] == config["talker_config"]["codec_language_id"]["english"] == 2050,
        "tensor_types_supported_by_pinned_ggml": {v["type"] for v in t.values()} <= {0, 12, 14} and {v["type"] for v in codec["tensors"].values()} <= {0, 1, 12},
        "codec_architecture": codec["metadata"]["general.architecture"] == "qwen3-tts-tokenizer",
        "codec_24khz_16_codebooks": codec["metadata"]["qwen3-tts-tokenizer.output_sample_rate"] == 24000 and codec["metadata"]["qwen3-tts-tokenizer.decoder.num_quantizers"] == 16,
        "codec_required_entry_tensors": all(name in codec["tensors"] for name in ["tok_dec.vq_first.0.codebook", "tok_dec.vq_rest.14.codebook", "tok_dec.pre_conv.weight", "tok_dec.pre_tfm.blk.7.attn_q.weight", "tok_dec.dec.6.conv.weight"]),
        "codec_convolution_conversion_types": len(runtime_f16) == 29 and all(tensor["type"] in {0, 1, 30} for tensor in runtime_f16.values()),
    }
    errors += [name for name, passed in checks.items() if not passed]
    result = {
        "scope": "static header/source-schema comparison; excludes native load, complete hash, synthesis, and device",
        "passed": not errors, "talker_tensor_shapes_checked": len(expected),
        "codec_tensor_count": codec["tensor_count"],
        "codec_decoder_tensor_count": sum(name.startswith("tok_dec.") for name in codec["tensors"]),
        "checks": checks, "errors": errors,
    }
    (audit / "format-check.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
