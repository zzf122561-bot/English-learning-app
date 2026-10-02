#include "probe_engine.h"
#include <jni.h>
#include <codecvt>
#include <locale>
#include <stdexcept>

namespace {
constexpr const char * params_class = "com/xuesui/englishapp/modelprobe/engines/qwen3tts/QwenNativeParameters";
constexpr const char * load_class = "com/xuesui/englishapp/modelprobe/engines/qwen3tts/QwenNativeLoadResult";
constexpr const char * result_class = "com/xuesui/englishapp/modelprobe/engines/qwen3tts/QwenNativeResult";

std::string utf8(JNIEnv * env, jstring input) {
    if (!input) throw std::invalid_argument("Null string");
    const jchar * chars = env->GetStringChars(input, nullptr);
    if (!chars) throw std::bad_alloc();
    const jsize length = env->GetStringLength(input);
    std::u16string text;
    try { text.assign(chars, chars + length); }
    catch (...) { env->ReleaseStringChars(input, chars); throw; }
    env->ReleaseStringChars(input, chars);
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    return converter.to_bytes(text);
}
jstring java_string(JNIEnv * env, const std::string & text) {
    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
    const auto value = converter.from_bytes(text);
    const auto result = env->NewString(reinterpret_cast<const jchar *>(value.data()), static_cast<jsize>(value.size()));
    if (!result) throw std::bad_alloc();
    return result;
}
void throw_java(JNIEnv * env, const char * type, const char * message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass(type);
    if (cls) env->ThrowNew(cls, message);
}
template<typename Result, typename Function> Result boundary(JNIEnv * env, Result fallback, Function body) {
    try { return body(); }
    catch (const std::invalid_argument & e) { throw_java(env, "java/lang/IllegalArgumentException", e.what()); }
    catch (const std::bad_alloc &) { throw_java(env, "java/lang/OutOfMemoryError", "Native allocation failed"); }
    catch (const std::exception & e) { throw_java(env, "java/lang/IllegalStateException", e.what()); }
    catch (...) { throw_java(env, "java/lang/IllegalStateException", "Unknown native failure"); }
    return fallback;
}
jfieldID field(JNIEnv * env, jclass cls, const char * name, const char * type) {
    const auto id = env->GetFieldID(cls, name, type);
    if (!id) throw std::runtime_error("JNI parameter contract mismatch");
    return id;
}
model_probe::Parameters parameters(JNIEnv * env, jobject input) {
    if (!input) throw std::invalid_argument("Null generation parameters");
    jclass cls = env->GetObjectClass(input);
    if (!cls) throw std::runtime_error("JNI parameter class unavailable");
    model_probe::Parameters p;
    p.language_id = env->GetIntField(input, field(env, cls, "languageId", "I"));
    p.speaker = utf8(env, static_cast<jstring>(env->GetObjectField(input, field(env, cls, "speaker", "Ljava/lang/String;"))));
    p.instruction = utf8(env, static_cast<jstring>(env->GetObjectField(input, field(env, cls, "instruction", "Ljava/lang/String;"))));
    p.threads = env->GetIntField(input, field(env, cls, "threads", "I"));
    p.temperature = env->GetFloatField(input, field(env, cls, "temperature", "F"));
    p.top_k = env->GetIntField(input, field(env, cls, "topK", "I"));
    p.top_p = env->GetFloatField(input, field(env, cls, "topP", "F"));
    p.repetition_penalty = env->GetFloatField(input, field(env, cls, "repetitionPenalty", "F"));
    p.max_audio_tokens = env->GetIntField(input, field(env, cls, "maxAudioTokens", "I"));
    p.seed = env->GetLongField(input, field(env, cls, "seed", "J"));
    return p;
}
jobject java_parameters(JNIEnv * env, const model_probe::Parameters & p) {
    jclass cls = env->FindClass(params_class);
    if (!cls) return nullptr;
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;Ljava/lang/String;IFIFFIJ)V");
    if (!constructor) return nullptr;
    return env->NewObject(cls, constructor, p.language_id, java_string(env, p.speaker), java_string(env, p.instruction),
        p.threads, static_cast<jfloat>(p.temperature), p.top_k, static_cast<jfloat>(p.top_p),
        static_cast<jfloat>(p.repetition_penalty), p.max_audio_tokens, static_cast<jlong>(p.seed));
}
jobject java_load(JNIEnv * env, const model_probe::LoadResult & result) {
    jclass cls = env->FindClass(load_class);
    if (!cls) return nullptr;
    auto ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IJ[Ljava/lang/String;Z)V");
    if (!ctor) return nullptr;
    jclass string_cls = env->FindClass("java/lang/String");
    if (!string_cls) return nullptr;
    auto names = env->NewObjectArray(static_cast<jsize>(result.speakers.size()), string_cls, nullptr);
    if (!names) return nullptr;
    for (size_t i = 0; i < result.speakers.size(); ++i) {
        auto name = java_string(env, result.speakers[i]);
        if (!name) return nullptr;
        env->SetObjectArrayElement(names, static_cast<jsize>(i), name);
        env->DeleteLocalRef(name);
        if (env->ExceptionCheck()) return nullptr;
    }
    return env->NewObject(cls, ctor, static_cast<jint>(result.status), java_string(env, result.error),
        java_string(env, result.effective.talker), java_string(env, result.effective.tokenizer),
        result.effective.threads, static_cast<jlong>(result.load_ms), names, static_cast<jboolean>(result.supports_instruction));
}
jobject java_result(JNIEnv * env, const model_probe::RunResult & result) {
    jclass cls = env->FindClass(result_class);
    if (!cls) return nullptr;
    auto ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;[FIJJJJZLcom/xuesui/englishapp/modelprobe/engines/qwen3tts/QwenNativeParameters;)V");
    if (!ctor) return nullptr;
    const auto & r = result.synthesis;
    auto pcm = env->NewFloatArray(static_cast<jsize>(r.audio.size()));
    if (!pcm) return nullptr;
    if (!r.audio.empty()) env->SetFloatArrayRegion(pcm, 0, static_cast<jsize>(r.audio.size()), r.audio.data());
    if (env->ExceptionCheck()) return nullptr;
    auto effective = result.parameters_applied ? java_parameters(env, result.effective) : nullptr;
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(cls, ctor, static_cast<jint>(result.status), java_string(env, result.error), pcm, r.sample_rate,
        static_cast<jlong>(r.t_tokenize_ms), static_cast<jlong>(r.t_generate_ms), static_cast<jlong>(r.t_decode_ms),
        static_cast<jlong>(r.t_total_ms), static_cast<jboolean>(r.hit_token_limit), effective);
}
} // namespace

#define JNI_NAME(name) Java_com_xuesui_englishapp_modelprobe_engines_qwen3tts_QwenNativeBindings_##name
extern "C" {
JNIEXPORT jlong JNICALL JNI_NAME(create)(JNIEnv * e, jobject) {
    return boundary<jlong>(e, 0, [] { return model_probe::create_session(); });
}
JNIEXPORT jlong JNICALL JNI_NAME(prepare)(JNIEnv * e, jobject, jlong h) {
    return boundary<jlong>(e, 0, [=] { return model_probe::prepare_operation(h); });
}
JNIEXPORT jboolean JNICALL JNI_NAME(cancel)(JNIEnv * e, jobject, jlong h, jlong op) {
    return boundary<jboolean>(e, false, [=] { return model_probe::cancel_operation(h, op); });
}
JNIEXPORT void JNICALL JNI_NAME(abandon)(JNIEnv * e, jobject, jlong h, jlong op) {
    boundary<int>(e, 0, [=] { model_probe::abandon_operation(h, op); return 0; });
}
JNIEXPORT void JNICALL JNI_NAME(close)(JNIEnv * e, jobject, jlong h) {
    boundary<int>(e, 0, [=] { model_probe::close_session(h); return 0; });
}
JNIEXPORT jint JNICALL JNI_NAME(phase)(JNIEnv * e, jobject, jlong h) {
    return boundary<jint>(e, 0, [=] { return model_probe::phase(h); });
}
JNIEXPORT jint JNICALL JNI_NAME(maximumThreads)(JNIEnv *, jobject) { return model_probe::maximum_threads(); }
JNIEXPORT jobject JNICALL JNI_NAME(nativeLoad)(JNIEnv * e, jobject, jlong h, jlong op, jstring talker, jstring codec, jint threads) {
    return boundary<jobject>(e, nullptr, [=] {
        auto result = model_probe::load(h, op, {utf8(e, talker), utf8(e, codec), threads});
        return java_load(e, result);
    });
}
JNIEXPORT jobject JNICALL JNI_NAME(nativeSynthesize)(JNIEnv * e, jobject, jlong h, jlong op, jstring text, jobject params) {
    return boundary<jobject>(e, nullptr, [=] {
        auto result = model_probe::synthesize(h, op, utf8(e, text), parameters(e, params));
        return java_result(e, result);
    });
}
}
