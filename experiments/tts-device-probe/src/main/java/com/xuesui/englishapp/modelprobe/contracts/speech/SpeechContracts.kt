package com.xuesui.englishapp.modelprobe.contracts.speech

import com.xuesui.englishapp.modelprobe.core.model.ModelDescriptor
import com.xuesui.englishapp.modelprobe.core.model.ModelLease
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import org.json.JSONObject

/** Typed implementations belong to each engine; generic runtime never parses Qwen fields. */
internal interface SpeechParameters {
    val schemaVersion: Int
    val threads: Int
    val randomSeed: Boolean
    fun validate()
    fun resolved(): SpeechParameters
    fun forSegment(index: Int): SpeechParameters
    fun snapshot(): JSONObject
    fun cacheParts(): List<String>
}
internal data class SpeechAudio(
    val pcm: FloatArray, val sampleRate: Int, val effective: SpeechParameters,
    val tokenizeMillis: Long, val generateMillis: Long, val decodeMillis: Long,
    val totalMillis: Long, val hitTokenLimit: Boolean,
)
internal class SpeechExecutionFailure(val cancelled: Boolean, val effective: SpeechParameters?, message: String) : RuntimeException(message)
internal interface SpeechEngine {
    val descriptor: ModelDescriptor
    val loaded: Boolean
    val instructionSupported: Boolean
    val speakers: List<String>
    fun load(lease: ModelLease, threads: Int, token: WorkToken): Long
    fun synthesize(text: String, parameters: SpeechParameters, token: WorkToken): SpeechAudio
    fun unload()
    fun phase(): String
    fun selfCheck(token: WorkToken): String
}
internal enum class SpeechMode { PARAGRAPH, SENTENCES }
internal enum class RunKind { COLD, WARM, CACHE }
internal data class SpeechRequest(val text: String, val parameters: SpeechParameters, val mode: SpeechMode, val kind: RunKind) {
    fun validate() {
        parameters.validate()
        require(text.isNotBlank() && '\u0000' !in text && text.codePointCount(0, text.length) <= 16384) { "请输入1至16384个字符的英文测试文本" }
    }
}
