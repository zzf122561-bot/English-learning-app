package com.xuesui.englishapp.modelprobe.features.speech

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.metrics.MetricsProvider
import com.xuesui.englishapp.modelprobe.core.model.ModelStore
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.text.BreakIterator
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

internal object SpeechText {
    const val SPLITTER = "breakiterator-en-v1-no-added-silence"
    fun sample(open: () -> InputStream): String = open().use { it.boundedText(65536) }.also {
        require(it.isNotBlank()) { "内置样例为空" }
        require(!it.any { c -> Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN }) { "内置样例含中文，不能作为英文样例朗读" }
    }
    fun segments(text: String, mode: SpeechMode): List<String> {
        if (mode == SpeechMode.PARAGRAPH) return listOf(text)
        val breaker = BreakIterator.getSentenceInstance(Locale.US); breaker.setText(text)
        val segments = mutableListOf<String>()
        var start = breaker.first(); var end = breaker.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(segments::add)
            start = end; end = breaker.next()
        }
        require(segments.size in 1..64) { "逐句模式限制为1至64句" }
        return segments
    }
    fun key(engine: SpeechEngine, request: SpeechRequest, resolved: SpeechParameters): String = hashParts(
        listOf("speech-cache-v2-validated-output", engine.descriptor.modelId, engine.descriptor.fingerprint, engine.descriptor.engineVersion,
            request.text, request.mode.name, SPLITTER) + resolved.cacheParts())
}

internal class WavWriter(file: File) : Closeable {
    private val out = RandomAccessFile(file, "rw")
    var samples = 0L; private set
    var sampleRate = 0; private set
    var clippedSamples = 0L; private set
    var peak = 0.0; private set
    var lastPeak = 0.0; private set
    private var squaredSum = 0.0
    private var sampleSum = 0.0
    fun pcmSnapshot() = JSONObject().put("finite", true).put("peak", peak)
        .put("rms", if (samples == 0L) 0.0 else kotlin.math.sqrt(squaredSum / samples))
        .put("dcMean", if (samples == 0L) 0.0 else sampleSum / samples).put("clippedSamples", clippedSamples)
    init { out.setLength(0); out.write(ByteArray(44)) }
    fun append(pcm: FloatArray, rate: Int, token: WorkToken) {
        require(rate in 8000..192000 && pcm.isNotEmpty()) { "原生音频为空或采样率无效" }
        require(sampleRate == 0 || rate == sampleRate) { "音频片段采样率不一致" }; sampleRate = rate
        require((samples + pcm.size) * 2 < 2_000_000_000L) { "WAV超过本实验大小限制" }
        val buffer = ByteArray(8192)
        lastPeak = 0.0
        var used = 0
        pcm.forEachIndexed { i, sample ->
            if (i % 4096 == 0) token.check()
            require(sample.isFinite()) { "原生音频包含NaN/Infinity" }
            peak = maxOf(peak, kotlin.math.abs(sample.toDouble()))
            lastPeak = maxOf(lastPeak, kotlin.math.abs(sample.toDouble()))
            squaredSum += sample.toDouble() * sample; sampleSum += sample
            if (sample < -1 || sample > 1) clippedSamples++
            val value = (sample.coerceIn(-1f, 1f) * 32767).toInt()
            buffer[used++] = value.toByte(); buffer[used++] = (value shr 8).toByte()
            if (used == buffer.size) { out.write(buffer); used = 0 }
        }
        if (used > 0) out.write(buffer, 0, used)
        samples += pcm.size
    }
    fun appendWave(file: File) {
        file.inputStream().use { input ->
            val header = ByteArray(44)
            var read = 0
            while (read < header.size) { val n = input.read(header, read, header.size - read); require(n > 0) { "WAV头不完整" }; read += n }
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 8, Charsets.US_ASCII) == "WAVEfmt " &&
                String(header, 36, 4, Charsets.US_ASCII) == "data" && h.getInt(16) == 16 && h.getShort(20).toInt() == 1 &&
                h.getShort(22).toInt() == 1 && h.getShort(34).toInt() == 16) { "队列只合并本模块的单声道PCM16 WAV" }
            val rate = h.getInt(24); val bytes = h.getInt(40).toLong()
            require(rate in 8000..192000 && bytes > 0 && bytes % 2 == 0L && file.length() == bytes + 44 &&
                h.getInt(4).toLong() == bytes + 36 && h.getInt(28) == rate * 2 && h.getShort(32).toInt() == 2) { "WAV长度或采样率无效" }
            require(sampleRate == 0 || sampleRate == rate) { "队列音频采样率不一致" }
            require(samples * 2 + bytes < 2_000_000_000L) { "合并音频超过本模块大小限制" }
            sampleRate = rate
            val buffer = ByteArray(65536)
            var copied = 0L
            while (copied < bytes) { val n = input.read(buffer, 0, minOf(buffer.size.toLong(), bytes - copied).toInt()); require(n > 0); out.write(buffer, 0, n); copied += n }
            require(input.read() == -1)
            samples += bytes / 2
        }
    }
    fun finish() {
        require(samples > 0 && sampleRate > 0)
        fun u32(value: Long) { repeat(4) { out.write((value shr (it * 8)).toInt() and 255) } }
        fun u16(value: Int) { out.write(value and 255); out.write(value shr 8 and 255) }
        out.seek(0); out.writeBytes("RIFF"); u32(samples * 2 + 36); out.writeBytes("WAVEfmt "); u32(16)
        u16(1); u16(1); u32(sampleRate.toLong()); u32(sampleRate * 2L); u16(2); u16(16)
        out.writeBytes("data"); u32(samples * 2); out.fd.sync()
    }
    override fun close() = out.close()
}

internal class SpeechService(
    val engine: SpeechEngine, private val models: ModelStore, val runs: RunStore,
    private val metrics: MetricsProvider, private val progress: (String) -> Unit,
) {
    fun load(threads: Int, token: WorkToken): Long {
        progress("校验私有模型副本")
        val lease = models.acquire(engine.descriptor, token)
        progress("载入1.7B权重；首次可能较慢")
        return engine.load(lease, threads, token)
    }
    fun runBatch(request: SpeechRequest, count: Int, token: WorkToken, saved: (SavedRun, Int) -> Unit) {
        require(count in 1..5 && (count == 1 || request.kind == RunKind.WARM))
        request.validate()
        val effective = request.parameters.resolved()
        repeat(count) { index ->
            token.check()
            val run = run(request, token, effective)
            saved(run, index)
            if (run.status != "success" && run.status != "cache") return
        }
    }
    fun run(request: SpeechRequest, token: WorkToken, batchParameters: SpeechParameters? = null, preferCache: Boolean = false): SavedRun {
        request.validate(); token.check()
        val resolved = (batchParameters ?: request.parameters.resolved()).also { it.validate() }
        val key = SpeechText.key(engine, request, resolved)
        if (request.kind == RunKind.CACHE) require(!request.parameters.randomSeed) { "随机seed每次不同，不能请求同参数缓存" }
        val cached = if (request.kind == RunKind.CACHE || (preferCache && !request.parameters.randomSeed)) runs.cached(key) else null
        val actualKind = if (cached != null) RunKind.CACHE else request.kind
        val descriptor = engine.descriptor
        val created = System.currentTimeMillis()
        val start = metrics.nowNanos()
        val report = JSONObject().put("taskId", descriptor.taskId).put("modelId", descriptor.modelId)
            .put("engineId", descriptor.engineId).put("engineVersion", descriptor.engineVersion)
            .put("modelRevision", descriptor.revision).put("modelFingerprint", descriptor.fingerprint).put("modelLicense", descriptor.license)
            .put("modelFiles", JSONArray().also { a -> descriptor.files.forEach { f -> a.put(JSONObject().put("role", f.role).put("name", f.name).put("bytes", f.sizeBytes).put("sha256", f.sha256)) } })
            .put("createdAtEpochMillis", created).put("runKind", actualKind.name.lowercase(Locale.ROOT))
            .put("reading", request.origin?.snapshot() ?: JSONObject.NULL)
            .put("mode", request.mode.name.lowercase(Locale.ROOT)).put("splitterVersion", SpeechText.SPLITTER)
            .put("textSha256", request.text.toByteArray(Charsets.UTF_8).sha256())
            .put("textCodePoints", request.text.codePointCount(0, request.text.length))
            .put("wordCountApproximate", Regex("\\b[A-Za-z]+(?:['’-][A-Za-z]+)*\\b").findAll(request.text).count())
            .put("requested", request.parameters.snapshot()).put("device", metrics.device())
            .put("backend", engine.runtimeInfo())
            .put("firstPcmLatencyMillis", JSONObject.NULL)
            .put("validation", JSONObject().put("compatibilityGate", "pending").put("listening", "unreviewed").put("offlineTest", "not_recorded"))
        runs.begin().use { draft ->
            val waveFile = draft.file("audio.wav")
            if (actualKind == RunKind.CACHE) {
                checkNotNull(cached) { "没有匹配且完整的缓存；请先真实生成" }
                cached.artifact("audio").inputStream().use { input -> waveFile.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) { token.check(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                } }
                report.put("status", "cache").put("sourceRunId", cached.id).put("audio", cached.report.getJSONObject("audio"))
                    .put("effectiveSegments", cached.report.getJSONArray("effectiveSegments")).put("loadMillis", 0)
                    .put("inferenceMillis", JSONObject.NULL).put("rtf", JSONObject.NULL).put("measurement", JSONObject.NULL)
                    .put("elapsedMillis", (metrics.nowNanos() - start) / 1_000_000)
                token.check()
                return draft.commit(report, mapOf("audio" to "audio.wav"))
            }
            val measurement = metrics.begin()
            var loadMillis = 0L
            val effective = JSONArray()
            var inferenceMillis = 0L
            var hitLimit = false
            var cacheMatches = true
            var wavWriteMillis = 0L
            var currentSegment = -1
            try {
                if (request.kind == RunKind.COLD) {
                    progress("冷启动：卸载旧引擎")
                    engine.unload(); token.check(); loadMillis = load(resolved.threads, token)
                } else check(engine.loaded) { "暖运行需要先载入模型" }
                val segments = SpeechText.segments(request.text, request.mode)
                report.put("requestedSegmentCount", segments.size)
                WavWriter(waveFile).use { wav ->
                    for ((index, text) in segments.withIndex()) {
                        currentSegment = index
                        token.check(); progress("生成 ${index + 1}/${segments.size} · ${if (request.mode == SpeechMode.PARAGRAPH) "完整段落" else "逐句试验"}")
                        val params = resolved.forSegment(index)
                        val audio = engine.synthesize(text, params, token)
                        token.check()
                        inferenceMillis += audio.generateMillis + audio.decodeMillis
                        hitLimit = hitLimit || audio.hitTokenLimit
                        cacheMatches = cacheMatches && audio.effective.cacheParts() == params.cacheParts()
                        effective.put(JSONObject().put("index", index).put("parameters", audio.effective.snapshot())
                            .put("samples", audio.pcm.size).put("sampleRate", audio.sampleRate).put("tokenizeMillis", audio.tokenizeMillis)
                            .put("generateMillis", audio.generateMillis).put("decodeMillis", audio.decodeMillis)
                            .put("nativeTotalMillis", audio.totalMillis).put("hitTokenLimit", audio.hitTokenLimit)
                            .put("textSha256", text.toByteArray(Charsets.UTF_8).sha256())
                            .put("textCodePoints", text.codePointCount(0, text.length))
                            .put("native", audio.diagnostics?.snapshot() ?: JSONObject.NULL))
                        check(cacheMatches) { "原生实际参数与请求不一致，结果不能作为完整音频保存" }
                        audio.diagnostics?.let { check(it.termination == if (audio.hitTokenLimit) "token_limit" else "eos") { "原生终止状态不一致" } }
                        val writeStarted = metrics.nowNanos()
                        wav.append(audio.pcm, audio.sampleRate, token)
                        wavWriteMillis += (metrics.nowNanos() - writeStarted) / 1_000_000
                        effective.getJSONObject(effective.length() - 1).put("pcmCumulative", wav.pcmSnapshot())
                        check(wav.lastPeak > 0.0) { "原生返回全静音，不能作为成功音频保存" }
                        if (audio.hitTokenLimit) break // Stop remaining sentences; retain and flag actual partial output.
                    }
                    token.check(); progress("保存WAV和报告")
                    val finishStarted = metrics.nowNanos()
                    wav.finish()
                    wavWriteMillis += (metrics.nowNanos() - finishStarted) / 1_000_000
                    val seconds = wav.samples.toDouble() / wav.sampleRate
                    report.put("audio", JSONObject().put("samples", wav.samples).put("sampleRate", wav.sampleRate)
                        .put("durationSeconds", seconds).put("channels", 1).put("pcmBits", 16).put("clippedSamples", wav.clippedSamples)
                        .put("pcm", wav.pcmSnapshot()))
                    report.put("rtf", inferenceMillis / 1000.0 / seconds)
                }
                token.check(); measurement.close()
                report.put("status", if (hitLimit) "token_limit" else "success").put("hitTokenLimit", hitLimit)
                    .put("effectiveSegments", effective).put("loadMillis", loadMillis).put("inferenceMillis", inferenceMillis)
                    .put("wavWriteMillis", wavWriteMillis)
                    .put("termination", if (hitLimit) "token_limit" else "eos")
                    .put("elapsedMillis", (metrics.nowNanos() - start) / 1_000_000).put("measurement", measurement.snapshot())
                report.getJSONObject("validation").put("nativeReturnedSuccessfully", true)
                return draft.commit(report, mapOf("audio" to "audio.wav"), if (!hitLimit && cacheMatches) key else null)
            } catch (failure: Throwable) {
                measurement.close()
                runCatching { engine.unload() }
                if (waveFile.exists()) check(waveFile.delete()) { "无法清理未完成音频" }
                report.put("status", if (failure is WorkCancelled || token.cancelled.get() || (failure is SpeechExecutionFailure && failure.cancelled)) "cancelled" else "failed")
                    .put("error", failure.message?.take(600) ?: failure.javaClass.simpleName)
                    .put("failedSegmentIndex", currentSegment)
                    .put("failedSegmentEffective", (failure as? SpeechExecutionFailure)?.effective?.snapshot() ?: JSONObject.NULL)
                    .put("effectiveSegments", effective).put("loadMillis", loadMillis).put("inferenceMillis", JSONObject.NULL)
                    .put("wavWriteMillis", wavWriteMillis)
                    .put("rtf", JSONObject.NULL).put("measurement", measurement.snapshot())
                    .put("elapsedMillis", (metrics.nowNanos() - start) / 1_000_000)
                return draft.commit(report, emptyMap())
            } finally { measurement.close() }
        }
    }
}
