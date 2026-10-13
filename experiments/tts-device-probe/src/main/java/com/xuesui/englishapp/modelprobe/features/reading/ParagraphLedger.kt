package com.xuesui.englishapp.modelprobe.features.reading

import com.xuesui.englishapp.modelprobe.core.storage.hashParts
import com.xuesui.englishapp.modelprobe.contracts.speech.SpeechParameters
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class BatchPhase { QUEUED, RUNNING, INTERRUPTED, PAUSED, FAILED, CANCELLED, COMPLETE }
internal enum class ParagraphPhase { PENDING, RUNNING, INTERRUPTED, FAILED, DONE, DELETED }
internal data class ReadyParagraph(val run: String, val parameters: String)
internal data class ParagraphJob(
    val id: String, val batch: String, val index: Int, val text: String, val bodyKey: String,
    val parameters: String, val phase: ParagraphPhase = ParagraphPhase.PENDING,
    val draft: String? = null, val run: String? = null, val reason: String = "",
) {
    fun json() = JSONObject().put("id", id).put("batch", batch).put("index", index).put("text", text)
        .put("body", bodyKey).put("parameters", parameters).put("phase", phase.name)
        .put("draft", draft ?: JSONObject.NULL).put("run", run ?: JSONObject.NULL).put("reason", reason)
    companion object {
        fun read(j: JSONObject) = ParagraphJob(j.getString("id"), j.getString("batch"), j.getInt("index"), j.getString("text"),
            j.getString("body"), j.getString("parameters"), ParagraphPhase.valueOf(j.getString("phase")), j.nullString("draft"), j.nullString("run"), j.getString("reason"))
    }
}
internal data class SpeechBatch(
    val id: String, val order: Long, val document: String, val title: String, val snapshotKey: String,
    val settingsKey: String, val engineKey: String, val requestedParameters: String,
    val phase: BatchPhase = BatchPhase.QUEUED, val reason: String = "",
) {
    fun json() = JSONObject().put("id", id).put("order", order).put("document", document).put("title", title)
        .put("snapshot", snapshotKey).put("settings", settingsKey).put("engine", engineKey).put("requested", requestedParameters)
        .put("phase", phase.name).put("reason", reason)
    companion object {
        fun read(j: JSONObject) = SpeechBatch(j.getString("id"), j.getLong("order"), j.getString("document"), j.getString("title"),
            j.getString("snapshot"), j.getString("settings"), j.getString("engine"), j.getString("requested"), BatchPhase.valueOf(j.getString("phase")), j.getString("reason"))
    }
}
internal data class ParagraphAudio(val document: String, val index: Int, val body: String, val settings: String, val engine: String, val run: String) {
    val key get() = key(document, index)
    fun json() = JSONObject().put("document", document).put("index", index).put("body", body).put("settings", settings).put("engine", engine).put("run", run)
    companion object {
        fun key(document: String, index: Int) = hashParts(listOf("paragraph-audio-v1", document, index.toString()))
        fun body(text: String) = hashParts(listOf(ReadingPlanner.VERSION, text))
        fun read(j: JSONObject) = ParagraphAudio(j.getString("document"), j.getInt("index"), j.getString("body"), j.getString("settings"), j.getString("engine"), j.getString("run"))
    }
}
internal data class LedgerState(
    val batches: List<SpeechBatch> = emptyList(), val jobs: List<ParagraphJob> = emptyList(),
    val latest: Map<String, ParagraphAudio> = emptyMap(), val garbage: Set<String> = emptySet(),
) {
    fun jobs(batch: String) = jobs.filter { it.batch == batch }.sortedBy { it.index }
    fun completed(batch: String) = jobs(batch).count { it.phase == ParagraphPhase.DONE }
    fun pins(): Set<String> {
        val live = batches.filter { it.phase !in setOf(BatchPhase.COMPLETE, BatchPhase.CANCELLED) }.map { it.id }.toSet()
        return latest.values.map { it.run }.toSet() + jobs.filter { it.batch in live }.flatMap { listOfNotNull(it.run, it.draft) }
    }
}

/** save is one durable transaction; throwing must leave the previous snapshot intact. */
internal interface QueuePersistence { fun load(): LedgerState; fun save(state: LedgerState) }

/** Platform-free transitions; SQLite owns persistence, the single runtime owns execution. */
internal class ParagraphLedger(private val persistence: QueuePersistence) {
    @Volatile var state = persistence.load(); private set
    @Synchronized private fun change(transform: (LedgerState) -> LedgerState) {
        val next = transform(state)
        persistence.save(next)
        state = next
    }
    @Synchronized fun enqueue(target: ReadingTarget, parameters: SpeechParameters, engineKey: String, ready: Map<Int, ReadyParagraph> = emptyMap()): String {
        require(target.plan.input.selection.scope != ReadingScope.SENTENCE)
        parameters.validate()
        val input = target.plan.input
        val indexes = if (input.selection.scope == ReadingScope.FULL) input.paragraphs.indices.toList() else listOf(input.selection.paragraph!!)
        val settings = ReadingTarget.settingsKey(parameters)
        val key = hashParts(listOf(target.documentId, target.bodyKey, target.key, settings, engineKey))
        state.batches.firstOrNull { it.snapshotKey == key && it.phase !in setOf(BatchPhase.CANCELLED, BatchPhase.COMPLETE, BatchPhase.FAILED) }?.let { return it.id }
        val id = UUID.randomUUID().toString()
        require(ready.keys.all { it in indexes })
        val batch = SpeechBatch(id, (state.batches.maxOfOrNull { it.order } ?: 0) + 1, target.documentId, input.title.take(200), key, settings, engineKey, parameters.snapshot().toString(),
            phase = if (ready.size == indexes.size) BatchPhase.COMPLETE else BatchPhase.QUEUED)
        val frozen = parameters.resolved()
        val jobs = indexes.map { index ->
            // Reserve 2048 deterministic attempt ordinals per natural paragraph. Retries use
            // the same frozen base after process death; random mode is resolved exactly once.
            val params = frozen.forSegment(index * 2048)
            val saved = ready[index]
            ParagraphJob(UUID.randomUUID().toString(), id, index, input.paragraphs[index], ParagraphAudio.body(input.paragraphs[index]), saved?.parameters ?: params.snapshot().toString(),
                phase = if (saved == null) ParagraphPhase.PENDING else ParagraphPhase.DONE, run = saved?.run)
        }
        change { it.copy(batches = it.batches + batch, jobs = it.jobs + jobs) }
        return id
    }
    @Synchronized fun claim(): ParagraphJob? {
        check(state.jobs.none { it.phase == ParagraphPhase.RUNNING }) { "上一原生任务尚未退出" }
        val batch = state.batches.sortedBy { it.order }.firstOrNull { b ->
            b.phase in setOf(BatchPhase.QUEUED, BatchPhase.INTERRUPTED, BatchPhase.RUNNING) && state.jobs(b.id).any { it.phase in setOf(ParagraphPhase.PENDING, ParagraphPhase.INTERRUPTED) }
        } ?: return null
        val job = state.jobs(batch.id).first { it.phase in setOf(ParagraphPhase.PENDING, ParagraphPhase.INTERRUPTED) }
        val claimed = job.copy(phase = ParagraphPhase.RUNNING, draft = null, reason = "")
        change { old -> old.copy(batches = old.batches.map { if (it.id == batch.id) it.copy(phase = BatchPhase.RUNNING, reason = "") else it },
            jobs = old.jobs.map { if (it.id == job.id) claimed else it }, garbage = old.garbage + listOfNotNull(job.draft)) }
        return claimed
    }
    fun adoptLegacy(audio: ParagraphAudio) = change { old ->
        if (audio.key in old.latest) old else old.copy(latest = old.latest + (audio.key to audio))
    }
    fun recordDraft(id: String, run: String) = change { old ->
        check(old.jobs.single { it.id == id }.phase == ParagraphPhase.RUNNING)
        old.copy(jobs = old.jobs.map { if (it.id == id) it.copy(draft = run) else it })
    }
    /** Called only after verifying the committed WAV/report and durably pinning its ID. */
    fun publish(id: String, run: String): Boolean {
        var accepted = false
        change { old ->
            val job = old.jobs.single { it.id == id }
            val batch = old.batches.single { it.id == job.batch }
            if (job.phase != ParagraphPhase.RUNNING || batch.phase !in setOf(BatchPhase.RUNNING, BatchPhase.QUEUED, BatchPhase.INTERRUPTED))
                return@change old.copy(garbage = old.garbage + run)
            accepted = true
            val audio = ParagraphAudio(batch.document, job.index, job.bodyKey, batch.settingsKey, batch.engineKey, run)
            val previous = old.latest[audio.key]?.run
            val jobs = old.jobs.map { if (it.id == id) it.copy(phase = ParagraphPhase.DONE, run = run, draft = null, reason = "") else it }
            val complete = jobs.filter { it.batch == batch.id }.all { it.phase == ParagraphPhase.DONE }
            old.copy(jobs = jobs, latest = old.latest + (audio.key to audio),
                batches = old.batches.map { if (it.id == batch.id) it.copy(phase = if (complete) BatchPhase.COMPLETE else BatchPhase.QUEUED, reason = "") else it },
                garbage = old.garbage + listOfNotNull(previous?.takeIf { it != run }))
        }
        return accepted
    }
    fun fail(id: String, reason: String) = change { old ->
        val job = old.jobs.single { it.id == id }; val batch = old.batches.single { it.id == job.batch }
        if (job.phase != ParagraphPhase.RUNNING || batch.phase != BatchPhase.RUNNING) old
        else old.copy(jobs = old.jobs.map { if (it.id == id) it.copy(phase = ParagraphPhase.FAILED, reason = reason.take(500)) else it },
            batches = old.batches.map { if (it.id == batch.id) it.copy(phase = BatchPhase.FAILED, reason = reason.take(500)) else it })
    }
    fun control(id: String, phase: BatchPhase, reason: String = "") = change { old ->
        require(phase in setOf(BatchPhase.PAUSED, BatchPhase.CANCELLED, BatchPhase.QUEUED, BatchPhase.INTERRUPTED))
        val batch = old.batches.single { it.id == id }
        require(batch.phase != BatchPhase.COMPLETE) { "已完成任务无需继续" }
        require(!(batch.phase == BatchPhase.CANCELLED && phase == BatchPhase.QUEUED)) { "已取消任务请重新生成" }
        old.copy(batches = old.batches.map { if (it.id == id) it.copy(phase = phase, reason = reason) else it },
            garbage = old.garbage + if (phase == BatchPhase.CANCELLED) old.jobs(id).mapNotNull { it.draft } else emptyList(),
            jobs = old.jobs.map { job -> if (job.batch != id || job.phase in setOf(ParagraphPhase.DONE, ParagraphPhase.DELETED)) job
                else job.copy(phase = if (phase == BatchPhase.QUEUED) ParagraphPhase.PENDING else ParagraphPhase.INTERRUPTED, reason = reason) })
    }
    fun interruptRunning(reason: String) = change { old -> old.copy(
        batches = old.batches.map { if (it.phase == BatchPhase.RUNNING) it.copy(phase = BatchPhase.INTERRUPTED, reason = reason) else it },
        jobs = old.jobs.map { if (it.phase == ParagraphPhase.RUNNING) it.copy(phase = ParagraphPhase.INTERRUPTED, reason = reason) else it }) }
    /** Explicit deletion fences ALL queued publications for the same paragraph. */
    fun deleteAudio(run: String) = change { old ->
        val keys = old.latest.values.filter { it.run == run }.map { it.key }.toSet()
        val affected = old.jobs.filter { job -> job.run == run || old.batches.single { it.id == job.batch }.let { ParagraphAudio.key(it.document, job.index) in keys } }.map { it.id }.toSet()
        val batches = old.jobs.filter { it.id in affected }.map { it.batch }.toSet()
        old.copy(latest = old.latest.filterValues { it.run != run }, garbage = old.garbage + run,
            jobs = old.jobs.map { if (it.id in affected) it.copy(phase = ParagraphPhase.DELETED, run = null, reason = "用户已删除；需明确重新生成") else it },
            batches = old.batches.map { if (it.id in batches && it.phase != BatchPhase.COMPLETE) it.copy(phase = BatchPhase.CANCELLED, reason = "用户删除音频，已停止相关任务") else it })
    }
    fun collected(ids: Set<String>) = change { it.copy(garbage = it.garbage - ids) }
}

internal fun JSONObject.nullString(key: String): String? = if (isNull(key)) null else getString(key)
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
