package com.xuesui.englishapp.modelprobe.features.reading

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import org.json.JSONArray
import org.json.JSONObject

internal data class ReadingProgress(val item: ReadingItem, val state: String, val run: SavedRun? = null)

internal object ReadingHistory {
    fun read(runs: RunStore, limit: Int = 100): List<SavedRun> {
        val records = runs.history(Int.MAX_VALUE, compact = true)
        val parents = records.filter { it.report.optString("taskId") == "speech.reading.v1" }.map { it.id }.toSet()
        val referenced = records.flatMap { run ->
            val items = run.report.optJSONArray("items")
            if (items == null) emptyList() else (0 until items.length()).map { items.getJSONObject(it).optString("runId") }
        }.toSet()
        return records.filter { run ->
            when (run.report.optString("taskId")) {
                "speech.reading.v1" -> true
                "speech.synthesis.v1" -> {
                    // Standalone legacy projects remain manageable even when a new parent reuses
                    // their audio; originated fragments are represented by their live parent.
                    val origin = run.report.optJSONObject("reading")
                    origin == null || (run.id !in referenced && origin.optString("sessionId") !in parents)
                }
                else -> false
            }
        }.take(limit)
    }
}

/** One serial queue; each child is durable before the next native call starts. */
internal class ReadingQueue(private val service: SpeechService, private val runs: RunStore,
    private val nowNanos: () -> Long = System::nanoTime) {
    fun generate(plan: ReadingPlan, parameters: SpeechParameters, token: WorkToken, reuse: Boolean,
        target: ReadingTarget? = null,
        progress: (ReadingProgress) -> Unit): SavedRun {
        parameters.validate(); token.check()
        val resolved = parameters.resolved()
        val started = nowNanos()
        val leases = mutableListOf<RunStore.Lease>()
        try { runs.begin().use { draft ->
            val children = mutableListOf<Pair<ReadingItem, SavedRun>>()
            val reusedItems = mutableSetOf<Int>()
            var status = "success"
            var error: String? = null
            try {
                for (item in plan.items) {
                    token.check()
                    progress(ReadingProgress(item, "running"))
                    val origin = SpeechOrigin(draft.id, plan.input.id, plan.input.title, plan.input.selection.scope.name,
                        item.index, plan.items.size, item.paragraph, item.firstSentence, item.lastSentence, item.start, item.end, ReadingPlanner.VERSION, target?.key)
                    val request = SpeechRequest(item.text, resolved.forSegment(item.index), SpeechMode.PARAGRAPH,
                        if (service.engine.loaded) RunKind.WARM else RunKind.COLD, origin)
                    val cached = if (target != null && reuse && !parameters.randomSeed)
                        runs.cachedLease(SpeechText.key(service.engine, request, request.parameters)) else null
                    val run = if (cached != null) { leases += cached; reusedItems += item.index; cached.run } else {
                        service.run(request, token, preferCache = target == null && reuse && !parameters.randomSeed).also {
                            leases += runs.acquire(it.id)
                        }
                    }
                    children += item to run
                    progress(ReadingProgress(item, if (item.index in reusedItems) "cache" else run.status, run))
                    if (run.status !in setOf("success", "cache")) { status = run.status; error = run.report.optString("error").ifBlank { null }; break }
                }
                if (token.cancelled.get()) status = "cancelled"
            } catch (failure: Throwable) {
                status = if (failure is WorkCancelled || token.cancelled.get()) "cancelled" else "failed"
                error = failure.message?.take(600) ?: failure.javaClass.simpleName
                runCatching { service.engine.unload() }
            }
            val report = JSONObject().put("taskId", "speech.reading.v1").put("title", plan.input.title)
                .put("sourceRequestId", plan.input.id).put("scope", plan.input.selection.scope.name)
                .put("plannerVersion", ReadingPlanner.VERSION).put("createdAtEpochMillis", System.currentTimeMillis())
                .put("modelId", service.engine.descriptor.modelId).put("engineVersion", service.engine.descriptor.engineVersion)
                .put("modelFingerprint", service.engine.descriptor.fingerprint).put("requested", parameters.snapshot())
                .put("resolvedBaseParameters", resolved.snapshot()).put("plannedCount", plan.items.size)
                .put("completedCount", children.count { it.second.status in setOf("success", "cache") })
                .put("status", status).put("error", error ?: JSONObject.NULL)
                .put("join", "pcm16-concatenation-no-added-silence").put("firstPcmLatencyMillis", JSONObject.NULL)
                .put("validation", JSONObject().put("compatibilityGate", "pending").put("listening", "unreviewed").put("offlineTest", "not_recorded"))
            val rows = JSONArray()
            plan.items.forEach { item ->
                val run = children.firstOrNull { it.first.index == item.index }?.second
                rows.put(JSONObject().put("index", item.index).put("label", item.label).put("paragraphIndex", item.paragraph)
                    .put("firstSentence", item.firstSentence).put("lastSentence", item.lastSentence)
                    .put("startOffset", item.start).put("endOffset", item.end).put("textSha256", item.textHash)
                    .put("status", if (item.index in reusedItems) "cache" else run?.status ?: "not_started")
                    .put("reused", item.index in reusedItems).put("runId", run?.id ?: JSONObject.NULL))
            }
            report.put("items", rows)
            report.put("childRunIds", JSONArray(children.map { it.second.id }.distinct()))
            target?.let { report.put("retentionKey", it.key).put("retention", it.snapshot())
                .put("settingsKey", ReadingTarget.settingsKey(parameters)) }
            val audio = children.map { it.second }.filter { it.report.has("audio") }
            var artifacts = emptyMap<String, String>()
            if (audio.isNotEmpty()) {
                try {
                    WavWriter(draft.file("audio.wav")).use { wav ->
                        audio.forEach { wav.appendWave(it.artifact("audio")) }
                        wav.finish()
                        report.put("audio", JSONObject().put("samples", wav.samples).put("sampleRate", wav.sampleRate)
                            .put("durationSeconds", wav.samples.toDouble() / wav.sampleRate).put("channels", 1).put("pcmBits", 16))
                    }
                    artifacts = mapOf("audio" to "audio.wav")
                } catch (failure: Throwable) {
                    report.put("status", "failed").put("error", "片段已分别保存，合并WAV失败：${failure.message?.take(500)}")
                    draft.file("audio.wav").delete()
                }
            }
            report.put("elapsedMillis", (nowNanos() - started) / 1_000_000)
            if (token.cancelled.get()) report.put("status", "cancelled")
            // Even after cancellation, finish the bounded file commit of completed children.
            return draft.commit(report, artifacts, publicationKey = target?.key?.takeIf { report.getString("status") == "success" })
        } } finally { leases.forEach { it.close() } }
    }
}
