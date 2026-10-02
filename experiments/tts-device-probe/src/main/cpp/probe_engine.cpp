#include "probe_engine.h"
#include "gguf_loader.h"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <filesystem>
#include <limits>
#include <memory>
#include <mutex>
#include <random>
#include <stdexcept>
#include <thread>

namespace model_probe {
namespace {
namespace fs = std::filesystem;
using Clock = std::chrono::steady_clock;
struct Session {
    std::mutex state;
    std::mutex execution;
    std::mutex close_mutex;
    bool closing = false;
    int64_t operation = 0;
    int64_t next_operation = 1;
    bool claimed = false;
    std::unique_ptr<qwen3_tts::Qwen3TTS> engine;
};
// Upstream has shared backend/thread configuration; enforce one owner process-wide.
std::mutex registry_mutex;
std::shared_ptr<Session> owner;
int64_t owner_handle = 0;
int64_t next_handle = 1;

std::shared_ptr<Session> borrow(int64_t handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    if (!owner || handle != owner_handle) throw std::invalid_argument("Invalid or closed native handle");
    return owner;
}

struct Operation {
    std::shared_ptr<Session> session;
    std::unique_lock<std::mutex> execution;
    int64_t id;
    bool finished = false;
    Operation(int64_t handle, int64_t operation)
        : session(borrow(handle)), execution(session->execution), id(operation) {
        std::lock_guard<std::mutex> lock(session->state);
        if (session->closing || session->operation != id || id == 0 || session->claimed)
            throw std::invalid_argument("Operation is stale, closed, or already executing");
        session->claimed = true;
    }
    void finish(Status & status, std::string & error) {
        std::unique_ptr<qwen3_tts::Qwen3TTS> discard;
        {
            std::lock_guard<std::mutex> lock(session->state);
            if (session->closing || qwen3_tts::probe_cancel_requested()) {
                status = Status::cancelled;
                error = "Cancelled; native execution has exited";
            }
            if (status != Status::success) discard = std::move(session->engine);
            session->operation = 0;
            session->claimed = false;
            finished = true;
        }
        // Still holds execution lock; no concurrent load/free, no callback data UAF.
        discard.reset();
        qwen3_tts::probe_set_phase(0);
    }
    ~Operation() {
        if (!finished) {
            Status status = Status::failed;
            std::string error;
            finish(status, error);
        }
    }
};

void validate_string(const std::string & value, size_t max_codepoints, const char * name) {
    size_t codepoints = 0;
    for (unsigned char c : value) {
        if (c == 0) throw std::invalid_argument(std::string(name) + " contains NUL");
        if ((c & 0xc0) != 0x80) ++codepoints;
    }
    if (codepoints > max_codepoints) throw std::invalid_argument(std::string(name) + " too long");
}
void validate_threads(int threads) {
    if (threads < 1 || threads > maximum_threads()) throw std::invalid_argument("Invalid CPU thread count");
}
ModelFiles exact_files(const ModelFiles & files) {
    validate_threads(files.threads);
    validate_string(files.talker, 32768, "talker path");
    validate_string(files.tokenizer, 32768, "tokenizer path");
    const fs::path talker = fs::canonical(fs::u8path(files.talker));
    const fs::path tokenizer = fs::canonical(fs::u8path(files.tokenizer));
    if (talker.filename() != "qwen-talker-1.7b-customvoice-Q4_K_M.gguf" ||
        tokenizer.filename() != "qwen-tokenizer-12hz-Q4_K_M.gguf" ||
        talker.parent_path() != tokenizer.parent_path() ||
        !fs::is_regular_file(talker) || !fs::is_regular_file(tokenizer) ||
        fs::file_size(talker) != 1182631296ULL || fs::file_size(tokenizer) != 254974752ULL)
        throw std::invalid_argument("Exact pinned model pair required; fallback is disabled");
    // Whole-file SHA256 is the model-install/lease layer's responsibility.
    return {talker.u8string(), tokenizer.u8string(), files.threads};
}
void set_threads(int threads) {
    if (!qwen3_tts::set_backend_preference(qwen3_tts::backend_preference::cpu) ||
        !qwen3_tts::set_cpu_thread_count(threads) || qwen3_tts::get_cpu_thread_count() != threads)
        throw std::runtime_error("CPU backend/thread request was not applied");
}
} // namespace

int maximum_threads() { return std::max(1u, std::thread::hardware_concurrency()); }

void validate(const Parameters & p) {
    validate_threads(p.threads);
    if (p.language_id != 2050) throw std::invalid_argument("English is required");
    if (p.speaker != "ryan" && p.speaker != "aiden") throw std::invalid_argument("Unsupported English speaker");
    validate_string(p.instruction, 512, "instruction");
    if (!std::isfinite(p.temperature) || p.temperature < 0 || p.temperature > 2 ||
        p.top_k < 0 || p.top_k > 2048 || !std::isfinite(p.top_p) || p.top_p <= 0 || p.top_p > 1 ||
        !std::isfinite(p.repetition_penalty) || p.repetition_penalty < 1 || p.repetition_penalty > 2 ||
        p.max_audio_tokens < 1 || p.max_audio_tokens > 4096 || p.seed < -1 ||
        p.seed > std::numeric_limits<int32_t>::max()) throw std::invalid_argument("Invalid generation parameters");
}
qwen3_tts::tts_params to_native(const Parameters & p) {
    validate(p);
    if (p.seed < 0) throw std::invalid_argument("Resolve random seed before native generation");
    qwen3_tts::tts_params result;
    result.language_id = p.language_id;
    result.speaker = p.speaker;
    result.instruction = p.instruction;
    result.n_threads = p.threads;
    result.temperature = p.temperature;
    result.top_k = p.top_k;
    result.top_p = p.top_p;
    result.repetition_penalty = p.repetition_penalty;
    result.max_audio_tokens = p.max_audio_tokens;
    result.seed = p.seed;
    result.print_progress = false;
    result.print_timing = false; // Private text/token IDs must not be logged.
    return result;
}
int64_t create_session() {
    std::lock_guard<std::mutex> lock(registry_mutex);
    if (owner) throw std::logic_error("Only one native model session may exist");
    owner = std::make_shared<Session>();
    owner_handle = next_handle++;
    return owner_handle;
}
int64_t prepare_operation(int64_t handle) {
    auto session = borrow(handle);
    std::lock_guard<std::mutex> lock(session->state);
    if (session->closing || session->operation) throw std::logic_error("Native session busy or closing");
    qwen3_tts::probe_reset_cancel();
    session->operation = session->next_operation++;
    session->claimed = false;
    return session->operation;
}
bool cancel_operation(int64_t handle, int64_t operation) {
    auto session = borrow(handle);
    std::lock_guard<std::mutex> lock(session->state);
    if (session->closing || session->operation != operation || operation == 0) return false;
    qwen3_tts::probe_request_cancel();
    return true;
}
void abandon_operation(int64_t handle, int64_t operation) {
    auto session = borrow(handle);
    std::lock_guard<std::mutex> lock(session->state);
    if (session->operation != operation || operation == 0) return;
    qwen3_tts::probe_request_cancel();
    if (!session->claimed) session->operation = 0;
}
LoadResult load(int64_t handle, int64_t operation, const ModelFiles & files) {
    Operation guard(handle, operation);
    LoadResult out;
    const auto start = Clock::now();
    try {
        if (!qwen3_tts::probe_cancel_requested()) {
            const auto checked = exact_files(files);
            guard.session->engine.reset();
            set_threads(files.threads);
            out.effective = checked;
            out.effective.threads = qwen3_tts::get_cpu_thread_count();
            guard.session->engine = std::make_unique<qwen3_tts::Qwen3TTS>();
            const fs::path talker = fs::u8path(out.effective.talker);
            if (guard.session->engine->load_models(talker.parent_path().u8string(), talker.filename().u8string(), out.effective.tokenizer)) {
                const auto capabilities = guard.session->engine->get_model_capabilities();
                out.speakers = guard.session->engine->get_available_speakers();
                out.supports_instruction = capabilities.supports_instruction;
                if (capabilities.model_type != "custom_voice" || !capabilities.supports_named_speakers ||
                    !out.supports_instruction || capabilities.speaker_embedding_dim != 2048 ||
                    std::find(out.speakers.begin(), out.speakers.end(), "ryan") == out.speakers.end() ||
                    std::find(out.speakers.begin(), out.speakers.end(), "aiden") == out.speakers.end())
                    throw std::runtime_error("Loaded model capabilities differ from the pinned descriptor");
                out.effective.threads = qwen3_tts::get_cpu_thread_count();
                out.status = Status::success;
            } else out.error = guard.session->engine->get_error();
        }
    } catch (const std::exception & e) { out.error = e.what(); }
    guard.finish(out.status, out.error);
    out.load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - start).count();
    return out;
}
RunResult synthesize(int64_t handle, int64_t operation, const std::string & text, const Parameters & requested) {
    Operation guard(handle, operation);
    RunResult out;
    out.effective = requested;
    try {
        validate(requested);
        validate_string(text, 16384, "text");
        if (text.empty() || text.find_first_not_of(" \r\n\t") == std::string::npos)
            throw std::invalid_argument("Text is empty");
        if (!qwen3_tts::probe_cancel_requested()) {
            if (!guard.session->engine || !guard.session->engine->is_loaded()) throw std::logic_error("Model is not loaded");
            if (out.effective.seed == -1) {
                std::random_device random;
                out.effective.seed = static_cast<int64_t>(random() & 0x7fffffffU);
            }
            set_threads(requested.threads);
            out.effective.threads = qwen3_tts::get_cpu_thread_count();
            const auto native = to_native(out.effective);
            out.parameters_applied = true;
            out.synthesis = guard.session->engine->synthesize(text, native);
            out.status = out.synthesis.success ? Status::success : Status::failed;
            out.error = out.synthesis.error_msg;
        }
    } catch (const std::exception & e) { out.error = e.what(); }
    guard.finish(out.status, out.error);
    if (out.status != Status::success) { out.synthesis.audio.clear(); out.synthesis.success = false; }
    return out;
}
void close_session(int64_t handle) {
    auto session = borrow(handle);
    std::lock_guard<std::mutex> close_lock(session->close_mutex);
    {
        std::lock_guard<std::mutex> lock(session->state);
        if (session->closing) return;
        session->closing = true;
        if (session->operation) qwen3_tts::probe_request_cancel();
    }
    std::lock_guard<std::mutex> join(session->execution);
    session->engine.reset();
    qwen3_tts::probe_set_phase(0);
    std::lock_guard<std::mutex> registry(registry_mutex);
    if (owner == session) { owner.reset(); owner_handle = 0; }
}
int phase(int64_t handle) { borrow(handle); return qwen3_tts::probe_get_phase(); }
} // namespace model_probe
