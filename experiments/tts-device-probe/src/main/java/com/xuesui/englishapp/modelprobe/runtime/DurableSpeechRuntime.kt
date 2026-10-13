package com.xuesui.englishapp.modelprobe.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenConfiguration
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.WavWriter
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal data class QueueUi(val ledger: LedgerState = LedgerState(), val ready: Boolean = false, val error: String = "", val revision: Long = 0)
internal data class PlayAvailability(val target: ReadingTarget, val audio: Map<Int, String>, val settingsKey: String) {
    val indexes get() = if (target.plan.input.selection.scope == ReadingScope.FULL) target.plan.input.paragraphs.indices.toList() else listOf(target.plan.input.selection.paragraph!!)
    val missing get() = indexes.size - audio.size
}

/** Process singleton, hosted by SpeechGenerationService; only ProbeController owns the model
 * worker. IO stays independent so playback/control is never queued behind minutes of inference. */
internal class DurableSpeechRuntime(private val app: Context, internal val owner: ProbeController) {
    private val main = Handler(Looper.getMainLooper())
    private val files = Executors.newSingleThreadExecutor { r -> Thread(r, "speech-ledger-io") }
    private val mutable = MutableStateFlow(QueueUi())
    val state = mutable.asStateFlow()
    private val ledger by lazy { ParagraphLedger(SqliteSpeechQueue(app, File(owner.root, "speech-queue.sqlite3"))) }
    private val engineKey = hashParts(listOf(owner.descriptor.fingerprint, owner.descriptor.engineVersion))
    @Volatile private var host: SpeechGenerationService? = null
    @Volatile private var activeJob: ParagraphJob? = null
    @Volatile private var serviceAllowed = true
    private var recovered = false
    @Volatile private var legacy: Pair<String, (WorkToken) -> Unit>? = null
    private var claiming = false
    private var fullExport: PlayAvailability? = null
    private val publicationLock = Any()
    private var keepModelLoaded = false
    fun io(title: String, action: () -> Unit) {
        files.execute {
            try { action() }
            catch (failure: Throwable) {
                val message = "$title：${failure.message ?: failure.javaClass.simpleName}"
                owner.report(message); mutable.value = mutable.value.copy(error = message)
                // A stopped worker must not leave an idle foreground service/wake lock alive
                // after storage/queue failure. No native teardown is attempted on this thread.
                if (!owner.state.value.busy) main.post { host?.finishWork() }
            }
        }
    }
    private fun publishUi() {
        mutable.value = QueueUi(ledger.state, true, revision = mutable.value.revision + 1)
        main.post { host?.updateNotification(); owner.playback.audioChanged() }
    }
    private fun reconcileFiles() = synchronized(publicationLock) {
        owner.runs.durablePins("queue", ledger.state.pins())
        val removed = ledger.state.garbage.filter { owner.runs.delete(it) }.toSet()
        if (removed.isNotEmpty()) ledger.collected(removed)
        owner.runs.recover()
    }
    private fun initialize() = synchronized(publicationLock) {
        if (recovered) return@synchronized
        // RunStore loads persistent pins before it performs recovery. Release stale playback
        // pins only on process initialization, when no player from the previous process exists.
        owner.runs.durablePins("player", emptySet())
        owner.runs.durablePins("export", emptySet())
        ledger.state.jobs.filter { it.phase == ParagraphPhase.RUNNING && it.draft != null }.forEach { job ->
            val run = runCatching { owner.runs.read(job.draft!!) }.getOrNull()
            if (run?.status == "success") {
                owner.runs.durablePins("queue", setOf(run.id), union = true)
                ledger.publish(job.id, run.id)
            }
        }
        ledger.interruptRunning("进程中断；已完成自然段保留")
        File(owner.root, "full-export.pending.wav").delete()
        reconcileFiles(); recovered = true; publishUi()
    }
    fun foreground() = io("恢复生成队列") {
        initialize(); serviceAllowed = true
        if (!owner.state.value.busy && activeJob == null) {
            ledger.interruptRunning("上次任务未正常结束；从未完成自然段继续")
        }
        if (eligible()) requestService() else publishUi()
    }
    fun enqueue(target: ReadingTarget, parameters: QwenConfiguration, existing: Map<Int, String> = emptyMap()) = io("加入生成队列") {
        initialize()
        check(owner.supportedAbi && owner.state.value.installed) { "请先到语音设置准备本地模型" }
        adoptLegacyParagraphs(target)
        synchronized(publicationLock) {
            // "Fill and play" preserves already approved audio even in random-seed mode.
            // Its effective seed stays attached to the reused run; only missing jobs get a new
            // resolved seed. Ordinary explicit generation may still request a new random take.
            val ready = existing.mapNotNull { (index, id) -> runCatching {
                val run = owner.runs.read(id)
                require(run.status == "success")
                val effective = run.report.optJSONObject("resolvedBaseParameters") ?: run.report.getJSONObject("requested")
                require(!QwenConfiguration.fromJson(effective).randomSeed)
                index to ReadyParagraph(id, effective.toString())
            }.getOrNull() }.toMap()
            owner.runs.durablePins("queue", ready.values.map { it.run }.toSet(), union = true)
            val id = ledger.enqueue(target, parameters, engineKey, ready)
            if (ledger.state.batches.single { it.id == id }.phase == BatchPhase.PAUSED) ledger.control(id, BatchPhase.QUEUED)
            reconcileFiles()
        }
        publishUi(); owner.report("已保存生成任务；参数和正文已冻结，切页后继续")
        serviceAllowed = true; requestService()
    }
    fun legacy(title: String, action: (WorkToken) -> Unit) = io("启动语音操作") {
        initialize()
        check(!owner.state.value.busy && legacy == null && activeJob == null && !eligible()) { "已有生成或模型操作，请等待完成或暂停" }
        legacy = title to action; serviceAllowed = true; requestService()
    }
    private fun requestService() {
        main.post {
            try { ContextCompat.startForegroundService(app, Intent(app, SpeechGenerationService::class.java)) }
            catch (failure: RuntimeException) {
                val message = "系统未允许启动后台生成；任务已保存，请回到前台继续：${failure.message}"
                owner.report(message); mutable.value = mutable.value.copy(error = message)
            }
        }
    }
    fun attach(service: SpeechGenerationService) { host = service; io("连接生成服务") { initialize(); pump() } }
    fun detach(service: SpeechGenerationService) {
        if (host !== service) return
        host = null
        if (owner.state.value.busy) interrupt("生成服务被系统停止；等待原生安全退出", service)
    }
    fun interrupt(reason: String, service: SpeechGenerationService) {
        serviceAllowed = false
        owner.cancelNative() // cancellation request only: never joins/free native on this thread.
        io("保存中断状态") { initialize(); ledger.interruptRunning(reason); publishUi() }
        owner.releaseWhenIdle()
        if (host === service) host = null
    }
    fun workerIdle() = io("继续生成队列") { initialize(); pump() }
    private fun eligible() = ledger.state.batches.any { it.phase in setOf(BatchPhase.QUEUED, BatchPhase.RUNNING, BatchPhase.INTERRUPTED) }
    private fun pump() {
        val attached = host ?: return
        if (!serviceAllowed || owner.state.value.busy || activeJob != null || claiming) return
        val pending = legacy
        if (pending != null) {
            legacy = null
            keepModelLoaded = true
            owner.exclusive(pending.first, pending.second)
            return
        }
        claiming = true
        try {
            val job = ledger.claim()
            if (job == null) {
                main.post {
                    if (host === attached && !owner.state.value.busy) {
                        if (!eligible() && legacy == null) attached.finishWork(releaseModel = !keepModelLoaded)
                        else workerIdle()
                    }
                }
                return
            }
            activeJob = job; keepModelLoaded = false; publishUi()
            main.post { if (host === attached) attached.ensureWake() }
            val accepted = owner.exclusive("生成第${job.index + 1}自然段") { token ->
                try {
                    val batch = ledger.state.batches.single { it.id == job.batch }
                    check(batch.engineKey == engineKey) { "任务使用旧引擎；请取消并按新设置重新生成" }
                    val parameters = QwenConfiguration.fromJson(JSONObject(job.parameters))
                    val old = ledger.state.latest[ParagraphAudio.key(batch.document, job.index)]
                    val reusable = old?.takeIf { it.body == job.bodyKey && it.settings == batch.settingsKey && it.engine == batch.engineKey &&
                        !QwenConfiguration.fromJson(JSONObject(batch.requestedParameters)).randomSeed }
                    val saved = reusable?.let { runCatching { owner.runs.read(it.run) }.getOrNull()?.takeIf { it.status == "success" } }
                        ?: ReadingQueue(owner.service, owner.runs).generate(
                            ReadingPlanner.create(ReadingInput(job.id, batch.title, List(job.index + 1) { if (it == job.index) job.text else "" },
                                ReadingSelection(ReadingScope.PARAGRAPH, job.index))), parameters, token, true,
                            durableDraft = { run -> synchronized(publicationLock) { ledger.recordDraft(job.id, run); owner.runs.durablePins("queue", setOf(run), union = true) } },
                            durableMetadata = JSONObject().put("schemaVersion", 1).put("documentId", batch.document)
                                .put("batchId", batch.id).put("jobId", job.id).put("paragraphIndex", job.index)
                                .put("label", "第${job.index + 1}自然段").put("bodyKey", job.bodyKey).put("settingsKey", batch.settingsKey)
                                .put("requestedBatchParameters", JSONObject(batch.requestedParameters)),
                            progress = { progress -> owner.report("第${job.index + 1}段 · 段内${progress.completed}/${progress.total}片；全文进度按完整自然段计") })
                    token.check()
                    if (saved.status != "success") error(saved.report.optString("error").ifBlank { "本段未完整生成：${saved.status}；需手动重试" })
                    synchronized(publicationLock) {
                        owner.runs.read(saved.id)
                        owner.runs.durablePins("queue", setOf(saved.id), union = true)
                        ledger.publish(job.id, saved.id)
                        reconcileFiles()
                    }
                } catch (failure: Throwable) {
                    // A user pause/cancel or timeout already changed the ledger; fail then is a
                    // no-op. A real paragraph failure pauses only this batch, never the next one.
                    if (!serviceAllowed) ledger.interruptRunning("系统或预算中断；重新打开App后继续")
                    else ledger.fail(job.id, failure.message ?: "生成中断")
                } finally { activeJob = null; publishUi() }
            }
            if (!accepted) {
                activeJob = null; ledger.interruptRunning("运行器正忙，等待退出"); publishUi()
            }
        } finally { claiming = false }
    }
    fun control(id: String, phase: BatchPhase) = io("更新生成任务") {
        initialize()
        synchronized(publicationLock) { ledger.control(id, phase, when (phase) { BatchPhase.PAUSED -> "用户暂停"; BatchPhase.CANCELLED -> "用户取消"; else -> "" }) }
        if (activeJob?.batch == id && phase != BatchPhase.QUEUED) { owner.cancelNative(); main.post { host?.releaseWake() } }
        reconcileFiles(); publishUi()
        if (phase == BatchPhase.QUEUED) { serviceAllowed = true; requestService() } else pump()
    }
    fun cancelActive() {
        val id = activeJob?.batch ?: return
        control(id, BatchPhase.CANCELLED)
    }
    fun deleteAudioNow(id: String) {
        initialize(); synchronized(publicationLock) { ledger.deleteAudio(id) }
        if (activeJob?.let { job -> ledger.state.jobs.single { it.id == job.id }.phase == ParagraphPhase.DELETED } == true) owner.cancelNative()
        reconcileFiles(); publishUi()
    }
    fun inspect(target: ReadingTarget, callback: (Result<PlayAvailability>) -> Unit) = io("读取播放范围") {
        initialize()
        adoptLegacyParagraphs(target)
        val result = runCatching { availability(target, verify = true) }
        main.post { callback(result) }
    }
    internal fun availability(target: ReadingTarget, verify: Boolean = false, settingsKey: String? = null): PlayAvailability {
        val settings = settingsKey ?: ReadingTarget.settingsKey(owner.form.value.parse())
        val indexes = if (target.plan.input.selection.scope == ReadingScope.FULL) target.plan.input.paragraphs.indices.toList() else listOf(target.plan.input.selection.paragraph!!)
        val audio = indexes.mapNotNull { index -> ledger.state.latest[ParagraphAudio.key(target.documentId, index)]?.takeIf {
            it.body == ParagraphAudio.body(target.plan.input.paragraphs[index]) && it.settings == settings && it.engine == engineKey
        }?.let { record ->
            if (verify && runCatching { owner.runs.read(record.run).status == "success" }.getOrDefault(false).not()) null else index to record.run
        } }.toMap()
        return PlayAvailability(target, audio, settings)
    }
    /** Only an explicitly indexed legacy PARAGRAPH is eligible. FULL files are never split or
     * matched by title/hash guesses; all old records remain available in audio management. */
    private fun adoptLegacyParagraphs(target: ReadingTarget) = synchronized(publicationLock) {
        val input = target.plan.input
        val indexes = if (input.selection.scope == ReadingScope.FULL) input.paragraphs.indices.toList() else listOf(input.selection.paragraph!!)
        indexes.forEach { index ->
            if (ParagraphAudio.key(target.documentId, index) !in ledger.state.latest) {
                val paragraph = ReadingTarget(target.documentId, ReadingPlanner.create(input.copy(selection = ReadingSelection(ReadingScope.PARAGRAPH, index))))
                val old = owner.runs.latest(paragraph.key)
                if (old?.status == "success") {
                    val body = old.report.getJSONObject("retention").getString("bodyKey")
                    val audio = ParagraphAudio(target.documentId, index,
                        if (body == paragraph.bodyKey) ParagraphAudio.body(input.paragraphs[index]) else "legacy:$body",
                        old.report.getString("settingsKey"), hashParts(listOf(old.report.getString("modelFingerprint"), old.report.getString("engineVersion"))), old.id)
                    owner.runs.durablePins("queue", setOf(old.id), union = true)
                    ledger.adoptLegacy(audio)
                }
            }
        }
        reconcileFiles()
    }
    fun prepareFullExport(target: ReadingTarget, launch: () -> Unit) = io("准备全文导出") {
        initialize()
        adoptLegacyParagraphs(target)
        val available = availability(target, true)
        require(available.missing == 0) { "全文尚未全部完成，请补齐后导出" }
        owner.runs.durablePins("export", available.audio.values.toSet())
        fullExport = available
        main.post(launch)
    }
    fun exportPreparedFull(uri: Uri?) {
        io("导出全文WAV") {
            val available = fullExport ?: return@io
            fullExport = null
            if (uri == null) { owner.runs.durablePins("export", emptySet()); reconcileFiles(); return@io }
            val ids = available.indexes.map { available.audio.getValue(it) }
            val temporary = File(owner.root, "full-export.pending.wav")
            owner.runs.durablePins("export", ids.toSet())
            val leases = mutableListOf<RunStore.Lease>()
            try {
                ids.forEach { leases += owner.runs.acquire(it) }
                WavWriter(temporary).use { writer -> leases.forEach { writer.appendWave(it.run.artifact("audio")) }; writer.finish() }
                app.contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output); temporary.inputStream().use { it.copyTo(output) }; output.flush()
                }
                owner.report("全文WAV已导出；段落按原顺序拼接")
            } catch (failure: Throwable) {
                runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                throw failure
            } finally {
                leases.forEach { it.close() }; temporary.delete(); owner.runs.durablePins("export", emptySet()); reconcileFiles()
            }
        }
    }
}
