package com.xuesui.englishapp.modelprobe.engines.qwen3tts

import java.io.File

/** Engine-owned DTO; keep field and constructor signatures stable with probe_jni.cpp. */
internal data class QwenNativeParameters(
    val languageId: Int = 2050,
    val speaker: String = "ryan",
    val instruction: String = "",
    val threads: Int = minOf(4, Runtime.getRuntime().availableProcessors()),
    val temperature: Float = 0.9f,
    val topK: Int = 50,
    val topP: Float = 1f,
    val repetitionPenalty: Float = 1.05f,
    val maxAudioTokens: Int = 1024,
    val seed: Long = 42,
) {
    fun validate(logicalCores: Int = Runtime.getRuntime().availableProcessors()) {
        require(languageId == 2050) { "当前验证仅支持英语" }
        require(speaker in setOf("ryan", "aiden")) { "不支持的英语音色" }
        require('\u0000' !in instruction && instruction.codePointCount(0, instruction.length) <= 512)
        require(threads in 1..maxOf(1, logicalCores))
        require(temperature.isFinite() && temperature in 0f..2f)
        require(topK in 0..2048)
        require(topP.isFinite() && topP > 0f && topP <= 1f)
        require(repetitionPenalty.isFinite() && repetitionPenalty in 1f..2f)
        require(maxAudioTokens in 1..4096)
        require(seed in -1L..Int.MAX_VALUE.toLong())
    }
}

/** Exact pair only. Full SHA256 verification belongs to the model lease/install layer. */
internal data class QwenModelFiles(val talker: File, val tokenizer: File, val threads: Int) {
    fun validate() {
        require(threads in 1..maxOf(1, Runtime.getRuntime().availableProcessors()))
        require(talker.canonicalFile.name == "qwen-talker-1.7b-customvoice-Q4_K_M.gguf")
        require(tokenizer.canonicalFile.name == "qwen-tokenizer-12hz-Q4_K_M.gguf")
        require(talker.canonicalFile.parentFile == tokenizer.canonicalFile.parentFile)
        require(talker.isFile && talker.length() == 1_182_631_296L)
        require(tokenizer.isFile && tokenizer.length() == 254_974_752L)
    }
}

// Status 0=success, 1=cancelled after native exit, 2=failed. No PCM on failure/cancel.
internal class QwenNativeLoadResult(
    val status: Int, val error: String,
    val talkerPath: String, val tokenizerPath: String, val effectiveThreads: Int,
    val loadMillis: Long, val speakers: Array<String>, val supportsInstruction: Boolean,
)
internal class QwenNativeResult(
    val status: Int, val error: String, val audio: FloatArray, val sampleRate: Int,
    val tokenizeMillis: Long, val generateMillis: Long, val decodeMillis: Long,
    val totalMillis: Long, val hitTokenLimit: Boolean,
    val effective: QwenNativeParameters?,
)
internal data class QwenRunSnapshot(val requested: QwenNativeParameters, val result: QwenNativeResult)

/** Minimal internal verification surface. Blocking methods must run on an owning worker.
 * Cancellation is explicit native work; cancelling a coroutine alone does not call it.
 * One prepared operation must be consumed or abandoned; close joins native execution.
 */
internal object QwenNativeBindings {
    init { System.loadLibrary("model_probe_qwen") }
    external fun create(): Long
    external fun prepare(handle: Long): Long
    external fun cancel(handle: Long, operation: Long): Boolean
    external fun abandon(handle: Long, operation: Long)
    external fun close(handle: Long)
    external fun phase(handle: Long): Int
    external fun maximumThreads(): Int

    // Kept callable from instrumentation for testing native validation independently.
    external fun nativeLoad(handle: Long, operation: Long, talker: String, tokenizer: String, threads: Int): QwenNativeLoadResult
    external fun nativeSynthesize(handle: Long, operation: Long, text: String, params: QwenNativeParameters): QwenNativeResult

    fun load(handle: Long, operation: Long, files: QwenModelFiles): QwenNativeLoadResult = try {
        files.validate()
        nativeLoad(handle, operation, files.talker.canonicalPath, files.tokenizer.canonicalPath, files.threads)
    } catch (failure: Throwable) {
        runCatching { abandon(handle, operation) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }

    fun synthesize(handle: Long, operation: Long, text: String, params: QwenNativeParameters): QwenRunSnapshot = try {
        params.validate()
        require(text.isNotBlank() && '\u0000' !in text && text.codePointCount(0, text.length) <= 16384)
        QwenRunSnapshot(params, nativeSynthesize(handle, operation, text, params))
    } catch (failure: Throwable) {
        runCatching { abandon(handle, operation) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}
