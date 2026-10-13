package com.xuesui.englishapp.modelprobe.features.reading

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import org.json.JSONArray
import org.json.JSONObject

internal data class ReadingProgress(val item: ReadingItem, val state: String, val run: SavedRun? = null, val total: Int = 0, val completed: Int = 0)

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

/** Each attempt is durable. Only accepted leaves are concatenated; retries replace exact slices. */
internal class ReadingQueue(private val service: SpeechService, private val runs: RunStore,
    private val nowNanos: () -> Long = System::nanoTime) {
    private data class Piece(val item: ReadingItem, val depth: Int = 0, var run: SavedRun? = null, var reused: Boolean = false)
    companion object { const val MAX_RETRY_DEPTH = 2; const val MAX_REPARTITIONS = 32 }
    fun generate(plan: ReadingPlan, parameters: SpeechParameters, token: WorkToken, reuse: Boolean,
        target: ReadingTarget? = null, durableDraft: ((String) -> Unit)? = null, durableMetadata: JSONObject? = null,
        progress: (ReadingProgress) -> Unit): SavedRun {
        parameters.validate(); token.check()
        val resolved = parameters.resolved()
        val execution = ReadingPlanner.create(plan.input, resolved.readingChunkCodePoints.coerceIn(1, ReadingPlanner.CHUNK_CODEPOINTS))
        val pieces = execution.items.map { Piece(it) }.toMutableList()
        val initialCount = pieces.size
        val started = nowNanos()
        val leases = mutableListOf<RunStore.Lease>()
        try { runs.begin().use { draft ->
            durableDraft?.invoke(draft.id)
            val attempts = mutableListOf<Pair<ReadingItem, SavedRun>>()
            val reusedIds = mutableSetOf<String>()
            var status = "success"
            var error: String? = null
            var completed = 0
            var repartitions = 0
            var firstCompletedMillis: Long? = null
            var cursor = 0
            try {
                while (cursor < pieces.size) {
                    token.check()
                    val piece = pieces[cursor]
                    val item = piece.item.copy(index = cursor)
                    progress(ReadingProgress(item, "running", total = pieces.size, completed = completed))
                    // Ordinal seeds remain the old sequence when no retry occurs. Retries have their
                    // own deterministic ordinals, recorded with the actual attempt/effective params.
                    val origin = SpeechOrigin(draft.id, plan.input.id, plan.input.title, plan.input.selection.scope.name,
                        cursor, pieces.size, item.paragraph, item.firstSentence, item.lastSentence, item.start, item.end,
                        ReadingPlanner.VERSION, target?.key, item.occurrenceCount)
                    val request = SpeechRequest(item.text, resolved.forSegment(attempts.size), SpeechMode.PARAGRAPH,
                        if (service.engine.loaded) RunKind.WARM else RunKind.COLD, origin)
                    val cached = if ((target != null || durableDraft != null) && reuse && !parameters.randomSeed)
                        runs.cachedLease(SpeechText.key(service.engine, request, request.parameters)) else null
                    val run = if (cached != null) { leases += cached; cached.run } else {
                        service.run(request, token, preferCache = target == null && durableDraft == null && reuse && !parameters.randomSeed).also {
                            leases += runs.acquire(it.id)
                        }
                    }
                    val reused = cached != null || run.status == "cache"
                    if (reused) reusedIds += run.id
                    piece.run = run; piece.reused = reused
                    attempts += item to run
                    if (run.status in setOf("success", "cache")) {
                        completed++
                        if (firstCompletedMillis == null) firstCompletedMillis = (nowNanos() - started) / 1_000_000
                        progress(ReadingProgress(item, if (reused) "cache" else run.status, run, pieces.size, completed))
                        cursor++
                        continue
                    }
                    // A token limit is recoverable only by bounded subdivision, never by accepting
                    // partial speech, raising the user's cap, or replaying the failed PCM.
                    val split = if (run.status == "token_limit" && !token.cancelled.get() && piece.depth < MAX_RETRY_DEPTH &&
                        repartitions < MAX_REPARTITIONS && pieces.size < ReadingPlanner.MAX_ITEMS)
                        ReadingPlanner.split(item, plan.input.paragraphs[item.paragraph]) else null
                    if (split != null) {
                        repartitions++
                        pieces.removeAt(cursor)
                        pieces.addAll(cursor, split.map { Piece(it, piece.depth + 1) })
                        progress(ReadingProgress(item, "repartitioning", run, pieces.size, completed))
                        continue
                    }
                    status = run.status
                    error = run.report.optString("error").takeIf { it.isNotBlank() && it != "null" }
                        ?: if (status == "token_limit") "当前片段达到生成上限；有限重分片未能完成，未替换旧完整音频" else null
                    progress(ReadingProgress(item, status, run, pieces.size, completed))
                    break
                }
                if (token.cancelled.get()) { status = "cancelled"; service.engine.unload() }
            } catch (failure: Throwable) {
                status = if (failure is WorkCancelled || token.cancelled.get()) "cancelled" else "failed"
                error = failure.message?.take(600) ?: failure.javaClass.simpleName
                runCatching { service.engine.unload() }
            }
            fun row(item: ReadingItem, run: SavedRun?, reused: Boolean): JSONObject = JSONObject()
                .put("index", item.index).put("label", item.label).put("paragraphIndex", item.paragraph)
                .put("firstSentence", if (item.occurrenceCount > 1) JSONObject.NULL else item.firstSentence)
                .put("lastSentence", if (item.occurrenceCount > 1) JSONObject.NULL else item.lastSentence)
                .put("startOffset", if (item.occurrenceCount > 1) JSONObject.NULL else item.start)
                .put("endOffset", if (item.occurrenceCount > 1) JSONObject.NULL else item.end)
                .put("textSha256", item.textHash).put("textCodePoints", item.text.codePointCount(0, item.text.length))
                .also { if (plan.input.selection.scope == ReadingScope.SENTENCE)
                    it.put("occurrence", if (item.occurrenceCount > 1) "unspecified" else "unique").put("matchingOccurrences", item.occurrenceCount) }
                .put("status", if (reused) "cache" else run?.status ?: "not_started")
                .put("reused", reused).put("runId", run?.id ?: JSONObject.NULL)
            val rows = JSONArray()
            pieces.forEachIndexed { i, piece -> rows.put(row(piece.item.copy(index = i), piece.run, piece.reused).put("retryDepth", piece.depth)) }
            val attemptRows = JSONArray()
            attempts.forEachIndexed { index, (item, run) ->
                val r = run.report
                attemptRows.put(row(item, run, run.id in reusedIds).put("attemptIndex", index)
                    .put("runKind", r.opt("runKind")).put("loadMillis", r.opt("loadMillis"))
                    .put("elapsedMillis", r.opt("elapsedMillis")).put("wavWriteMillis", r.opt("wavWriteMillis"))
                    .put("effectiveSegments", r.opt("effectiveSegments"))
                    .put("audio", r.opt("audio")).put("error", r.opt("error")))
            }
            val fresh = attempts.map { it.second }.filter { it.id !in reusedIds }
            val segments = fresh.flatMap { run -> run.report.optJSONArray("effectiveSegments")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty() }
            fun sumField(field: String) = segments.sumOf { it.optLong(field) }
            val native = segments.mapNotNull { it.optJSONObject("native") }
            val timing = JSONObject().put("loadMillis", fresh.sumOf { it.report.optLong("loadMillis") })
                .put("tokenizeMillis", sumField("tokenizeMillis")).put("generateIncludingPrefillMillis", sumField("generateMillis"))
                .put("decodeMillis", sumField("decodeMillis")).put("childWavWriteMillis", fresh.sumOf { it.report.optLong("wavWriteMillis") })
                .put("prefillMillis", if (native.size == segments.size && native.isNotEmpty()) native.sumOf { it.getLong("prefillMillis") } else JSONObject.NULL)
                .put("tokenGenerationMillis", if (native.size == segments.size && native.isNotEmpty()) native.sumOf { it.getLong("tokenGenerationMillis") } else JSONObject.NULL)
                .put("scope", "available-returned-segments-including-retries")
                .put("attemptsWithReturnedSegments", fresh.count { (it.report.optJSONArray("effectiveSegments")?.length() ?: 0) > 0 })
                .put("freshAttempts", fresh.size).put("cacheInferenceExcluded", true)
            val report = JSONObject().put("taskId", "speech.reading.v1").put("title", plan.input.title)
                .put("sourceRequestId", plan.input.id).put("scope", plan.input.selection.scope.name)
                .put("plannerVersion", ReadingPlanner.VERSION).put("createdAtEpochMillis", System.currentTimeMillis())
                .put("modelId", service.engine.descriptor.modelId).put("engineVersion", service.engine.descriptor.engineVersion)
                .put("modelFingerprint", service.engine.descriptor.fingerprint).put("requested", parameters.snapshot())
                .put("resolvedBaseParameters", resolved.snapshot()).put("initialPlannedCount", initialCount).put("plannedCount", pieces.size)
                .put("chunkCodePoints", resolved.readingChunkCodePoints).put("repartitions", repartitions)
                .put("maxRetryDepth", MAX_RETRY_DEPTH).put("maxRepartitions", MAX_REPARTITIONS)
                .put("completedCount", completed).put("status", status).put("error", error ?: JSONObject.NULL)
                .put("join", "pcm16-concatenation-no-added-silence").put("firstPcmLatencyMillis", JSONObject.NULL)
                .put("firstCompletedSegmentMillis", firstCompletedMillis ?: JSONObject.NULL)
                .put("timing", timing).put("items", rows).put("attempts", attemptRows)
                .put("childRunIds", JSONArray(attempts.map { it.second.id }.distinct()))
                .put("validation", JSONObject().put("compatibilityGate", "pending").put("listening", "unreviewed").put("offlineTest", "not_recorded"))
            target?.let { report.put("retentionKey", it.key).put("retention", it.snapshot())
                .put("settingsKey", ReadingTarget.settingsKey(parameters)) }
            durableMetadata?.let { report.put("durableParagraph", it) }
            // Keep only final leaves: discarded partial attempts stay in childRunIds for evidence
            // and safe reclamation, but are not concatenated again before successful retries.
            val audio = pieces.mapNotNull { it.run }.filter { it.report.has("audio") }
            var artifacts = emptyMap<String, String>()
            val mergeStarted = nowNanos()
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
            timing.put("mergeWavMillis", (nowNanos() - mergeStarted) / 1_000_000)
            report.put("elapsedMillis", (nowNanos() - started) / 1_000_000)
            if (token.cancelled.get()) report.put("status", "cancelled")
            return draft.commit(report, artifacts, publicationKey = target?.key?.takeIf { report.getString("status") == "success" })
        } } finally { leases.forEach { it.close() } }
    }
}
