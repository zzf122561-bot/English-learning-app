package com.xuesui.englishapp.modelprobe.stage2

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.metrics.*
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal val tinyBytes = mapOf("talker" to "synthetic talker".toByteArray(), "tokenizer" to "synthetic codec".toByteArray())
internal fun tinyDescriptor() = ModelDescriptor("synthetic-model", "fake-engine", "test-only-1", "speech.synthesis.v1", "r1", "test-data", "Synthetic", "fake", "test", setOf("test"),
    tinyBytes.map { (role, bytes) -> ModelFile(role, "$role.gguf", "models/$role.gguf", bytes.size.toLong(), bytes.sha256()) })
internal fun manifest(d: ModelDescriptor) = JSONObject().put("schemaVersion", 1).put("models", JSONArray().put(JSONObject()
    .put("modelId", d.modelId).put("engineId", d.engineId).put("taskId", d.taskId).put("revision", d.revision).put("license", d.license)
    .put("files", JSONArray().also { array -> d.files.forEach { f -> array.put(JSONObject().put("role", f.role).put("name", f.name).put("assetPath", f.assetPath).put("sizeBytes", f.sizeBytes).put("sha256", f.sha256)) } })))
internal fun install(store: ModelStore, d: ModelDescriptor) = store.install(d, WorkToken(), { tinyBytes.getValue(it.role).inputStream() }) { _, _ -> }
internal fun rejected(body: () -> Unit) {
    var rejected = false
    try { body() } catch (_: Exception) { rejected = true }
    org.junit.Assert.assertTrue("Operation should reject invalid input", rejected)
}
internal class FakeMetrics : MetricsProvider {
    private var now = 0L
    override fun nowNanos(): Long { now += 1_000_000; return now }
    override fun device() = JSONObject().put("evidence", "synthetic-jvm-test")
    override fun begin() = object : Measurement {
        override fun close() {}
        override fun snapshot() = JSONObject().put("evidence", "fake-sampler")
    }
}
internal class FakeSpeechEngine(override val descriptor: ModelDescriptor) : SpeechEngine {
    override var loaded = false
    override val instructionSupported = true
    override val speakers = listOf("ryan", "aiden")
    private var lease: ModelLease? = null
    var loads = 0
    var calls = 0
    var unloads = 0
    var action: ((WorkToken) -> Unit)? = null
    var limit = false
    val requests = mutableListOf<SpeechParameters>()
    override fun load(lease: ModelLease, threads: Int, token: WorkToken): Long {
        unload(); token.check(); this.lease = lease; loaded = true; loads++; return 100
    }
    override fun synthesize(text: String, parameters: SpeechParameters, token: WorkToken): SpeechAudio {
        check(loaded); calls++; requests.add(parameters); action?.invoke(token); token.check()
        return SpeechAudio(FloatArray(240) { if (it % 2 == 0) 0.2f else -0.2f }, 24000, parameters, 3, 20, 5, 28, limit)
    }
    override fun unload() { unloads++; lease?.close(); lease = null; loaded = false }
    override fun phase() = "fake"
    override fun selfCheck(token: WorkToken) = "fake"
}
