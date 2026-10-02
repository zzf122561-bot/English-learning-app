#pragma once

#include "qwen3_tts.h"
#include <cstdint>
#include <string>
#include <vector>

namespace model_probe {
// Engine-owned types, intentionally not a generic model/task API.
struct Parameters {
    int language_id = 2050;
    std::string speaker = "ryan";
    std::string instruction;
    int threads = 1;
    float temperature = 0.9f;
    int top_k = 50;
    float top_p = 1.0f;
    float repetition_penalty = 1.05f;
    int max_audio_tokens = 1024;
    int64_t seed = 42;
};
struct ModelFiles { std::string talker; std::string tokenizer; int threads = 1; };
enum class Status : int { success = 0, cancelled = 1, failed = 2 };
struct LoadResult {
    Status status = Status::failed;
    std::string error;
    ModelFiles effective{"", "", 0};
    int64_t load_ms = 0;
    std::vector<std::string> speakers;
    bool supports_instruction = false;
};
struct RunResult {
    Status status = Status::failed;
    std::string error;
    Parameters effective;
    bool parameters_applied = false;
    qwen3_tts::tts_result synthesis;
};

void validate(const Parameters & params);
qwen3_tts::tts_params to_native(const Parameters & effective);
int maximum_threads();
int64_t create_session();
int64_t prepare_operation(int64_t handle);
bool cancel_operation(int64_t handle, int64_t operation);
void abandon_operation(int64_t handle, int64_t operation);
LoadResult load(int64_t handle, int64_t operation, const ModelFiles & files);
RunResult synthesize(int64_t handle, int64_t operation, const std::string & text, const Parameters & requested);
void close_session(int64_t handle); // Blocking join: invoke on an owning worker, never on UI.
int phase(int64_t handle);
} // namespace model_probe
