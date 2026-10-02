#include "probe_engine.h"
#include "gguf_loader.h"
#include "ggml-cpu.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <thread>

static void check(bool condition, const char * message) {
    if (!condition) throw std::runtime_error(message);
}
template<class F> static void rejects(F body) {
    bool rejected = false;
    try { body(); } catch (const std::exception &) { rejected = true; }
    check(rejected, "Expected rejection");
}

int main() {
    try {
        model_probe::Parameters p;
        p.language_id = 2050; p.speaker = "aiden"; p.instruction = "Speak calmly.";
        p.temperature = 0.7f; p.top_k = 17; p.top_p = 0.8f;
        p.repetition_penalty = 1.2f; p.max_audio_tokens = 96; p.seed = 123456;
        const auto native = model_probe::to_native(p);
        check(native.language_id == 2050 && native.speaker == "aiden" && native.instruction == p.instruction &&
            native.n_threads == 1 && native.temperature == 0.7f && native.top_k == 17 && native.top_p == 0.8f &&
            native.repetition_penalty == 1.2f && native.max_audio_tokens == 96 && native.seed == 123456,
            "Requested parameters did not reach actual C++ generation type");
        auto invalid = p; invalid.temperature = std::numeric_limits<float>::quiet_NaN();
        rejects([&] { model_probe::validate(invalid); });
        invalid = p; invalid.threads = 0; rejects([&] { model_probe::validate(invalid); });
        invalid = p; invalid.seed = -2; rejects([&] { model_probe::validate(invalid); });
        invalid = p; invalid.top_p = 0; rejects([&] { model_probe::validate(invalid); });
        invalid = p; invalid.language_id = 2055; rejects([&] { model_probe::validate(invalid); });

        const auto h = model_probe::create_session();
        rejects([] { model_probe::create_session(); });
        auto op = model_probe::prepare_operation(h);
        rejects([&] { model_probe::prepare_operation(h); });
        check(model_probe::cancel_operation(h, op), "Pre-execution cancellation not accepted");
        auto cancelled = model_probe::load(h, op, {"must-not-be-read", "must-not-be-read", 1});
        check(cancelled.status == model_probe::Status::cancelled, "Cancelled load read or accepted missing files");
        auto next = model_probe::prepare_operation(h);
        check(!model_probe::cancel_operation(h, op), "Stale cancellation affected next operation");
        auto failed = model_probe::synthesize(h, next, "Hello.", p);
        check(failed.status == model_probe::Status::failed && !failed.parameters_applied && failed.synthesis.audio.empty(),
            "Unloaded session produced an effective snapshot or PCM");
        auto prepared = model_probe::prepare_operation(h);
        model_probe::abandon_operation(h, prepared);
        prepared = model_probe::prepare_operation(h);
        std::thread cancel([&] { model_probe::cancel_operation(h, prepared); });
        cancel.join();
        auto stopped = model_probe::synthesize(h, prepared, "Hello.", p);
        check(stopped.status == model_probe::Status::cancelled && stopped.synthesis.audio.empty(), "Cancellation result contract");
        model_probe::close_session(h);
        rejects([&] { model_probe::prepare_operation(h); });
        const auto fresh = model_probe::create_session();
        model_probe::close_session(fresh);

        // Actual pinned GGML CPU backend test, not a mock or a model inference.
        qwen3_tts::probe_reset_cancel();
        check(qwen3_tts::set_cpu_thread_count(1), "CPU setter");
        std::string error;
        auto backend = qwen3_tts::init_cpu_backend("contract-test", &error);
        check(backend != nullptr && qwen3_tts::get_cpu_thread_count() == 1, "CPU thread readback");
        ggml_init_params init{1024 * 1024, nullptr, false};
        auto ctx = ggml_init(init);
        auto input = ggml_new_tensor_1d(ctx, GGML_TYPE_F32, 1024);
        ggml_set_f32(input, 1.0f);
        auto first = ggml_sqr(ctx, input);
        auto output = ggml_sqr(ctx, first);
        auto graph = ggml_new_graph(ctx);
        ggml_build_forward_expand(graph, output);
        qwen3_tts::probe_request_cancel();
        check(ggml_backend_graph_compute(backend, graph) == GGML_STATUS_ABORTED, "CPU graph did not acknowledge abort");
        qwen3_tts::probe_reset_cancel();
        check(ggml_backend_graph_compute(backend, graph) == GGML_STATUS_SUCCESS, "CPU graph did not recover after abort");
        ggml_backend_free(backend);
        ggml_free(ctx);
        std::cout << "PASS native contracts: parameter mapping, ownership, stale/pre-cancel, CPU abort/recovery. No model inference.\n";
        return 0;
    } catch (const std::exception & e) {
        std::cerr << "FAIL native contracts: " << e.what() << '\n';
        return 1;
    }
}
