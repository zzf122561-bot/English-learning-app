package com.xuesui.englishapp.modelprobe.engines.qwen3tts

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import java.security.SecureRandom
import org.json.JSONObject

internal object QwenModel {
    val descriptor = ModelDescriptor(
        modelId = "qwen3-tts-1.7b-customvoice-q4", engineId = "qwen3tts-cpu",
        engineVersion = "16bb5afc-ggml-af97976c-patch-08f2da86-adapter2", taskId = "speech.synthesis.v1",
        revision = "b7ee2e8c7459c3bea99da23e3d178125a7d1713c", license = "Apache-2.0",
        title = "Qwen3-TTS · 1.7B CustomVoice · Q4", runtime = "GGUF / GGML CPU", abi = "arm64-v8a",
        capabilities = setOf("named-speaker", "instruction", "batch-synthesis", "cooperative-cancel"),
        files = listOf(
            ModelFile("talker", "qwen-talker-1.7b-customvoice-Q4_K_M.gguf", "models/qwen-talker-1.7b-customvoice-Q4_K_M.gguf", 1_182_631_296L, "cc328834a631bc08bf9f43e62fa23f8a1383d9b429864ce6690cfb172077fc4a"),
            ModelFile("tokenizer", "qwen-tokenizer-12hz-Q4_K_M.gguf", "models/qwen-tokenizer-12hz-Q4_K_M.gguf", 254_974_752L, "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039"),
        ),
    )
}

internal data class QwenConfiguration(val native: QwenNativeParameters = QwenNativeParameters()) : SpeechParameters {
    override val schemaVersion = 1
    override val threads get() = native.threads
    override val randomSeed get() = native.seed == -1L
    override fun validate() = native.validate()
    override fun resolved() = if (randomSeed) copy(native = native.copy(seed = SecureRandom().nextInt(Int.MAX_VALUE).toLong())) else this
    override fun forSegment(index: Int): QwenConfiguration {
        require(!randomSeed && index >= 0)
        return copy(native = native.copy(seed = (native.seed + index) % (Int.MAX_VALUE.toLong() + 1)))
    }
    override fun snapshot() = JSONObject().put("schemaVersion", schemaVersion).put("languageId", native.languageId)
        .put("speaker", native.speaker).put("instruction", native.instruction).put("threads", native.threads)
        .put("temperature", native.temperature.toDouble()).put("topK", native.topK).put("topP", native.topP.toDouble())
        .put("repetitionPenalty", native.repetitionPenalty.toDouble()).put("maxAudioTokens", native.maxAudioTokens).put("seed", native.seed)
    override fun cacheParts() = listOf(schemaVersion.toString(), native.languageId.toString(), native.speaker, native.instruction,
        native.threads.toString(), native.temperature.toString(), native.topK.toString(), native.topP.toString(),
        native.repetitionPenalty.toString(), native.maxAudioTokens.toString(), native.seed.toString())
    companion object {
        fun fromJson(j: JSONObject): QwenConfiguration {
            require(j.getInt("schemaVersion") == 1) { "未知Qwen参数版本" }
            return QwenConfiguration(QwenNativeParameters(j.getInt("languageId"), j.getString("speaker"), j.getString("instruction"),
                j.getInt("threads"), j.getDouble("temperature").toFloat(), j.getInt("topK"), j.getDouble("topP").toFloat(),
                j.getDouble("repetitionPenalty").toFloat(), j.getInt("maxAudioTokens"), j.getLong("seed"))).also { it.validate() }
        }
    }
}

internal class QwenSpeechEngine : SpeechEngine {
    override val descriptor = QwenModel.descriptor
    @Volatile private var handle = 0L
    private var lease: ModelLease? = null
    @Volatile override var loaded = false; private set
    override var instructionSupported = false; private set
    override var speakers = emptyList<String>(); private set
    private inline fun <T> nativeOperation(token: WorkToken, body: (Long, Long) -> T): T {
        token.check()
        val h = handle
        val operation = QwenNativeBindings.prepare(h)
        token.attach { runCatching { QwenNativeBindings.cancel(h, operation) } }.use {
            try { return body(h, operation) }
            catch (error: Throwable) { runCatching { QwenNativeBindings.abandon(h, operation) }; throw error }
        }
    }
    override fun load(lease: ModelLease, threads: Int, token: WorkToken): Long {
        try {
            unload()
            token.check()
            handle = QwenNativeBindings.create()
            this.lease = lease
            val result = nativeOperation(token) { h, op ->
                QwenNativeBindings.load(h, op, QwenModelFiles(lease.files.getValue("talker"), lease.files.getValue("tokenizer"), threads))
            }
            if (result.status == 1) throw WorkCancelled()
            check(result.status == 0) { result.error.ifBlank { "原生模型加载失败" } }
            token.check()
            loaded = true; instructionSupported = result.supportsInstruction; speakers = result.speakers.toList()
            return result.loadMillis
        } catch (error: Throwable) {
            runCatching { unload() }
            if (this.lease !== lease) lease.close()
            throw error
        }
    }
    override fun synthesize(text: String, parameters: SpeechParameters, token: WorkToken): SpeechAudio {
        require(parameters is QwenConfiguration)
        check(loaded) { "请先载入模型" }
        try {
            val result = nativeOperation(token) { h, op -> QwenNativeBindings.synthesize(h, op, text, parameters.native).result }
            if (result.status != 0 || token.cancelled.get()) throw SpeechExecutionFailure(
                result.status == 1 || token.cancelled.get(), result.effective?.let(::QwenConfiguration),
                result.error.ifBlank { if (token.cancelled.get()) "已取消，原生调用已退出" else "原生合成失败" })
            return SpeechAudio(result.audio, result.sampleRate, QwenConfiguration(checkNotNull(result.effective)),
                result.tokenizeMillis, result.generateMillis, result.decodeMillis, result.totalMillis, result.hitTokenLimit)
        } catch (error: Throwable) { runCatching { unload() }; throw error }
    }
    override fun unload() {
        if (handle != 0L) QwenNativeBindings.close(handle) // Blocking join; only owning worker calls this.
        handle = 0; loaded = false; speakers = emptyList(); instructionSupported = false
        lease?.close(); lease = null
    }
    override fun phase(): String = if (handle == 0L) "" else runCatching {
        when (QwenNativeBindings.phase(handle)) { 1 -> "载入权重"; 2 -> "处理文本 / 预填充"; 3 -> "生成音频编码"; 4 -> "解码音频"; else -> "等待原生返回" }
    }.getOrDefault("")
    override fun selfCheck(token: WorkToken): String {
        unload(); token.check()
        val h = QwenNativeBindings.create()
        try {
            val op = QwenNativeBindings.prepare(h)
            check(QwenNativeBindings.cancel(h, op))
            val result = QwenNativeBindings.nativeLoad(h, op, "must-not-be-read", "must-not-be-read", 1)
            check(result.status == 1 && result.effectiveThreads == 0)
            val next = QwenNativeBindings.prepare(h)
            check(!QwenNativeBindings.cancel(h, op))
            val invalid = QwenNativeBindings.nativeSynthesize(h, next, "Hello.", QwenNativeParameters(topP = Float.NaN))
            check(invalid.status == 2 && invalid.effective == null && invalid.audio.isEmpty())
            return "JNI无模型契约自检通过：预取消、过期取消隔离、非法参数拒绝。未验证模型/运行中CPU取消。"
        } finally { QwenNativeBindings.close(h) }
    }
}
