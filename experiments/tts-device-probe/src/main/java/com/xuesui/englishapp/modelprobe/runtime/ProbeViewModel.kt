package com.xuesui.englishapp.modelprobe.runtime

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.metrics.AndroidMetrics
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import com.xuesui.englishapp.modelprobe.features.reading.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Composition root: only this layer selects the concrete trusted engine and its editor/codec. */
internal class ProbeViewModel(context: Context, private val diagnostic: Boolean = true, private val privateDirectory: File? = null) : ViewModel(), SpeechActions, ReadingActions {
    private val app = context.applicationContext as android.app.Application
    val descriptor = QwenModel.descriptor
    val supportedAbi = Build.SUPPORTED_ABIS.contains(descriptor.abi)
    private val mutable = MutableStateFlow(SpeechUiState())
    val state = mutable.asStateFlow()
    private val mutableForm = MutableStateFlow(QwenForm.from(QwenConfiguration()))
    val form = mutableForm.asStateFlow()
    private val mutableReading = MutableStateFlow(ReadingUiState())
    val reading = mutableReading.asStateFlow()
    private val inbox = ReadingInbox()
    private val entryMemory = ReadingEntryMemory()
    private var standaloneAfterExit = false
    private var switchAfterExit = false
    private var releaseAfterExit = false
    @Volatile private var initialized = false
    private val root by lazy { (privateDirectory ?: File(app.noBackupFilesDir, "model-probe-v1")).also { it.mkdirs() } }
    private val models by lazy { ModelStore(File(root, "models")) }
    private val runs by lazy { RunStore(File(root, "results"), File(root, "cache-index")) }
    private val settings by lazy { SettingsStore(File(root, "settings")) }
    private val engine: SpeechEngine = QwenSpeechEngine()
    private val metrics = AndroidMetrics(app)
    private val service by lazy { SpeechService(engine, models, runs, metrics) { message -> mutable.update { it.copy(message = message) } } }
    private val main = Handler(Looper.getMainLooper())
    private val playback = SpeechPlayback(AndroidAudioFactory(app)) { value ->
        mutable.update { it.copy(playback = value.label) }
        mutableReading.update { it.copy(playback = value) }
    }
    private var sample: String? = null
    private var talker: Uri? = null
    private var tokenizer: Uri? = null
    private val exports = mutableMapOf<String, String>()
    private var documentSelection = false
    @Volatile private var disposed = false
    @Volatile private var started = 0L
    private val runner = TaskRunner { busy, cancelling ->
        mutable.update { it.copy(busy = busy, cancelling = cancelling, phase = if (busy) it.phase else "", progress = if (busy) it.progress else null) }
        if (!busy) main.post { onWorkerIdle() }
    }
    private val ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "probe-progress").also { it.isDaemon = true } }
    init {
        ticker.scheduleWithFixedDelay({
            if (state.value.busy) mutable.update { it.copy(phase = engine.phase(), elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000_000) }
        }, 500, 500, TimeUnit.MILLISECONDS)
        work("读取样例与模型清单") {
            val sampleResult = runCatching { if (diagnostic) SpeechText.sample { app.assets.open("probe-sample.txt") } else "" }
            sample = if (diagnostic) sampleResult.getOrNull() else null
            val manifest = runCatching { app.assets.open("models/manifest.json").use { ModelCatalog(listOf(descriptor)).parseManifest(it.boundedText(65536)) } }
            var settingMessage = ""
            val (config, preferences) = runCatching {
                settings.load(descriptor.modelId, descriptor.engineVersion, 2)?.let {
                    QwenConfiguration.fromJson(it.getJSONObject("generation")) to SpeechPreferences.fromJson(it.getJSONObject("speech"))
                } ?: (QwenConfiguration() to SpeechPreferences())
            }.getOrElse { settingMessage = "设置读取失败，已恢复默认，原文件保留：${it.message}"; QwenConfiguration() to SpeechPreferences() }
            mutableForm.value = QwenForm.from(config)
            mutable.update { it.copy(text = sample.orEmpty(), sampleAvailable = sample != null,
                sampleMessage = if (!diagnostic) "" else if (sample != null) "已读入宿主固定样例，可编辑或恢复" else "样例缺失或无效：${sampleResult.exceptionOrNull()?.message}；可手动输入",
                manifestReady = manifest.isSuccess, manifestMessage = if (manifest.isSuccess) "内置模型清单身份匹配；安装时校验完整副本" else "内置模型清单不可用：${manifest.exceptionOrNull()?.message}",
                mode = preferences.mode, batchCount = preferences.batchCount, playbackSpeed = preferences.playbackSpeed,
                cacheAllowed = !config.randomSeed, message = settingMessage.ifBlank { if (diagnostic) "实验性设备验证候选；请先安装并载入模型" else "选择英文和朗读范围，点击生成后开始。音频保存后可以反复播放。" }) }
            main.post { if (!disposed) playback.setSpeed(preferences.playbackSpeed) }
            initialized = true
        }
    }
    private fun refresh() {
        val presence = runCatching { models.present(descriptor) }
        val installed = presence.getOrDefault(false)
        val history = if (diagnostic) runs.history() else ReadingHistory.read(runs)
        mutable.update { it.copy(installed = installed, loaded = engine.loaded, modelIssue = presence.exceptionOrNull()?.message.orEmpty(),
            instructionSupported = !engine.loaded || engine.instructionSupported, history = history,
            hasSynthesis = it.hasSynthesis || history.any { run -> run.status == "success" || run.status == "token_limit" }) }
    }
    private fun work(title: String, action: (WorkToken) -> Unit) {
        if (disposed) return
        runner.submit { token ->
            started = SystemClock.elapsedRealtimeNanos()
            mutable.update { it.copy(message = title, elapsedSeconds = 0, progress = null) }
            try { action(token) }
            catch (error: Throwable) { mutable.update { it.copy(message = if (error is WorkCancelled) "取消完成，任务已退出" else "操作失败：${error.message ?: error.javaClass.simpleName}") } }
            finally { runCatching { refresh() }.onFailure { failure -> mutable.update { it.copy(message = "记录读取失败：${failure.message}") } } }
        }
    }
    override fun editText(text: String) { if (!state.value.busy) mutable.update { it.copy(text = text) } }
    override fun restoreSample() { if (!state.value.busy) sample?.let { text -> mutable.update { it.copy(text = text, message = "已恢复宿主固定样例") } } }
    fun editForm(form: QwenForm) {
        if (state.value.busy) return
        mutableForm.value = form
        val valid = runCatching { form.parse() }
        mutable.update { it.copy(parametersValid = valid.isSuccess, cacheAllowed = valid.getOrNull()?.randomSeed == false,
            parameterError = if (valid.isSuccess) "" else "参数无效：请检查范围、整数、有限数及指令长度；当前不能生成或保存") }
    }
    override fun setMode(mode: SpeechMode) { if (!state.value.busy) mutable.update { it.copy(mode = mode) } }
    override fun setBatch(count: Int) { if (!state.value.busy && count in 1..5) mutable.update { it.copy(batchCount = count) } }
    override fun saveSettings(reset: Boolean) {
        val form = if (reset) QwenForm.from(QwenConfiguration()) else mutableForm.value
        val current = state.value
        val preferences = if (reset) SpeechPreferences() else SpeechPreferences(current.mode, current.batchCount, current.playbackSpeed)
        work("保存全部设置") { _ ->
            val params = form.parse(); preferences.validate()
            settings.save(descriptor.modelId, descriptor.engineVersion, 2, JSONObject().put("generation", params.snapshot()).put("speech", preferences.snapshot()))
            mutableForm.value = form
            mutable.update { it.copy(parametersValid = true, parameterError = "", cacheAllowed = !params.randomSeed,
                mode = preferences.mode, batchCount = preferences.batchCount, playbackSpeed = preferences.playbackSpeed,
                message = if (reset) "已恢复并保存全部默认设置" else "已保存生成参数、模式、批次和播放速度；历史结果保留原参数") }
            main.post { if (!disposed) setPlaybackSpeed(preferences.playbackSpeed) }
        }
    }
    fun chooseImport(role: String, uri: Uri?) {
        if (uri == null || state.value.busy) return
        if (role == "talker") talker = uri else if (role == "tokenizer") tokenizer = uri
        mutable.update { it.copy(talkerChosen = talker != null, tokenizerChosen = tokenizer != null) }
    }
    override fun install(fromImport: Boolean) {
        val sources = mapOf("talker" to talker, "tokenizer" to tokenizer)
        work(if (fromImport) "校验并安装所选模型" else "从内置资产安装；约1.44GB") { token ->
            check(!engine.loaded) { "请先卸载模型" }
            if (fromImport) check(sources.values.all { it != null }) { "请分别选择talker与tokenizer两个文件" }
            else check(state.value.manifestReady) { "内置模型清单未通过，不能安装" }
            models.install(descriptor, token, open = { spec ->
                if (fromImport) app.contentResolver.openInputStream(sources.getValue(spec.role)!!) ?: error("无法打开${spec.role}文件")
                else app.assets.open(spec.assetPath)
            }) { done, total -> mutable.update { it.copy(progress = done.toFloat() / total, message = "安装与SHA256校验：${done / 1_000_000} / ${total / 1_000_000} MB") } }
            mutable.update { it.copy(message = "两个私有副本完整校验并安装成功；尚未载入模型") }
        }
    }
    override fun verify() = work("完整校验已安装模型") { token ->
        check(!engine.loaded) { "请先卸载后校验" }
        models.acquire(descriptor, token, true) { done, total -> mutable.update { it.copy(progress = done.toFloat() / total) } }.use { }
        mutable.update { it.copy(message = "私有模型副本的完整SHA256校验通过") }
    }
    override fun load() {
        val form = mutableForm.value
        work("载入模型") { token ->
            check(supportedAbi) { "当前设备ABI不受支持，要求arm64-v8a" }
            val time = service.load(form.parse().threads, token)
            mutable.update { it.copy(message = "原生载入成功：${time}ms；尚未证明可以合成") }
        }
    }
    override fun unload() = work("等待原生退出并卸载") { _ -> engine.unload(); mutable.update { it.copy(message = "模型已卸载，历史结果保留") } }
    override fun deleteModel() = work("删除私有模型副本") { _ -> models.delete(descriptor); mutable.update { it.copy(message = "私有模型已删除，内置资产和历史结果保留") } }
    override fun generate(kind: RunKind, batch: Boolean) {
        stopPlayback()
        val captured = state.value
        val capturedForm = mutableForm.value
        work("准备生成") { token ->
            check(supportedAbi) { "当前设备ABI不受支持" }
            val request = SpeechRequest(captured.text, capturedForm.parse(), captured.mode, kind)
            val count = if (batch) captured.batchCount else 1
            service.runBatch(request, count, token) { saved, index ->
                mutable.update { it.copy(selected = saved, message = "${index + 1}/$count · ${runStatusTitle(saved.status)}") }
            }
        }
    }
    override fun clearCache() = work("清理缓存索引") { _ -> runs.clearCache(); mutable.update { it.copy(message = "缓存索引已清理；已保存WAV和历史报告仍可重播/导出") } }
    override fun select(id: String) = work("读取历史结果") { _ ->
        val run = runs.read(id); mutable.update { it.copy(selected = run, message = "已选择历史结果；参数见结果详情") }
    }
    override fun selfCheck() = work("JNI无模型契约自检") { token ->
        val message = engine.selfCheck(token)
        runs.begin().use { draft ->
            val saved = draft.commit(JSONObject().put("taskId", "native.contract.v1").put("modelId", descriptor.modelId)
                .put("engineVersion", descriptor.engineVersion).put("createdAtEpochMillis", System.currentTimeMillis())
                .put("status", "self_check").put("evidence", "jni_contract_only_no_model").put("device", metrics.device()).put("message", message), emptyMap())
            mutable.update { it.copy(selected = saved, message = message) }
        }
    }
    override fun cancel() { runner.cancel(); mutable.update { if (it.busy) it.copy(message = "已请求取消，仍在等待原生/文件操作退出；不会提前释放") else it } }
    override fun setPlaybackSpeed(speed: Float) {
        if (!speed.isFinite() || speed !in 0.5f..2f) return
        mutable.update { it.copy(playbackSpeed = speed) }
        playback.setSpeed(speed)
    }
    override fun play(id: String) {
        stopPlayback()
        val generation = playback.epoch
        work("核对所选音频") { token ->
            val run = runs.read(id)
            val file = run.artifact("audio")
            mutable.update { it.copy(selected = run) }
            token.check()
            main.post { if (!disposed && generation == playback.epoch && !token.cancelled.get()) playback.play(file, id) }
        }
    }
    override fun stopPlayback() = playback.stop()
    override fun pausePlayback() = playback.pause()
    override fun resumePlayback() = playback.resume()
    fun prepareExport(role: String): Boolean {
        if (state.value.busy) return false
        val id = state.value.selected?.id ?: return false
        exports[role] = id; return true
    }
    fun selectingDocument(selecting: Boolean) { documentSelection = selecting; if (selecting) stopPlayback() }
    fun hostStopped() { if (documentSelection) stopPlayback() else pageStopped() }
    fun export(role: String, uri: Uri?) {
        val id = exports.remove(role) ?: return
        if (uri == null) return
        work("导出所选结果") { token ->
            try {
                val run = runs.read(id)
                val file = if (role == "report") child(run.directory, "report.json") else run.artifact("audio")
                app.contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output) { "无法打开导出目标" }
                    file.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) { token.check(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                    }
                    output.flush()
                }
                mutable.update { it.copy(message = "已导出结果 ${id.take(8)} 的${if (role == "report") "JSON报告" else "WAV音频"}") }
            } catch (failure: Throwable) {
                val removed = runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }.getOrDefault(false)
                throw IllegalStateException("导出未完成：${failure.message}；${if (removed) "已清理新建目标" else "目标可能包含部分内容，请在文件管理器检查"}", failure)
            }
        }
    }
    fun enterReading(request: SpeechReadingRequest?) {
        if (request == null) {
            inbox.dismiss(); switchAfterExit = false
            if (entryMemory.articleId != null && state.value.busy) {
                standaloneAfterExit = true; cancel()
                mutableReading.update { it.copy(switching = true, pendingTitle = null) }
            } else if (entryMemory.articleId != null) openStandalone()
            else mutableReading.update { it.copy(pendingTitle = null, switching = false) }
            return
        }
        standaloneAfterExit = false
        val input = ReadingInput(request.requestId, request.title, request.englishParagraphs.toList(),
            ReadingSelection(ReadingScope.valueOf(request.scope.name), request.paragraphIndex, request.sentenceText))
        try {
            when (inbox.offer(input, state.value.busy && initialized)) {
                RequestOffer.OPENED -> openArticle(input)
                RequestOffer.PENDING -> mutableReading.update { it.copy(pendingTitle = input.title.ifBlank { "新的文章朗读" }, requestError = "") }
                RequestOffer.DUPLICATE -> {
                    if (entryMemory.articleId != input.id && !state.value.busy) {
                        stopPlayback()
                        mutableReading.value = entryMemory.article(input.id, reading.value, null)
                        mutable.update { it.copy(selected = reading.value.selectedSession) }
                    }
                }
            }
        } catch (error: Exception) { mutableReading.update { it.copy(requestError = error.message ?: "朗读请求无效") } }
    }
    private fun openArticle(input: ReadingInput) {
        stopPlayback()
        val plan = ReadingPlanner.create(input)
        val fresh = ReadingUiState(title = input.title.ifBlank { "文章朗读" }, text = input.paragraphs.joinToString("\n\n"),
            input = plan.input, selection = input.selection, rows = plan.items.map { ReadingRow(it.label, it.text.trim().take(160)) })
        mutableReading.value = entryMemory.article(input.id, reading.value, fresh)
        mutable.update { it.copy(selected = null, message = "已准备朗读范围；请确认设置并点击生成") }
        switchAfterExit = false
    }
    private fun openStandalone() {
        stopPlayback()
        mutableReading.value = entryMemory.standalone(reading.value).copy(pendingTitle = null, switching = false)
        mutable.update { it.copy(selected = reading.value.selectedSession) }
        standaloneAfterExit = false
    }
    override fun switchReadingRequest() {
        if (inbox.pending == null) return
        switchAfterExit = true
        mutableReading.update { it.copy(switching = true) }
        if (state.value.busy) cancel() else onWorkerIdle()
    }
    override fun keepReadingRequest() {
        inbox.dismiss(); switchAfterExit = false
        mutableReading.update { it.copy(pendingTitle = null, switching = false) }
    }
    private fun onWorkerIdle() {
        if (disposed || state.value.busy) return
        if (releaseAfterExit) {
            releaseAfterExit = false
            if (engine.loaded) { work("释放语音模型内存") { engine.unload() }; return }
        }
        if (standaloneAfterExit) openStandalone()
        else if (switchAfterExit) inbox.takePending(false)?.let(::openArticle)
    }
    override fun editReadingText(text: String) {
        if (state.value.busy || text.length > ReadingPlanner.MAX_TEXT) return
        val selection = ReadingSelection()
        val input = ReadingInput("manual-${UUID.randomUUID()}", "我的朗读", ReadingPlanner.paragraphs(text), selection)
        mutableReading.update { it.copy(title = "我的朗读", text = text, input = input, selection = selection, selectedSession = null) }
        rebuildReadingPreview()
    }
    override fun chooseReadingScope(scope: ReadingScope) {
        if (state.value.busy) return
        val current = reading.value
        val paragraphs = current.input?.paragraphs.orEmpty()
        val index = current.selection.paragraph?.takeIf { it in paragraphs.indices } ?: 0
        val sentence = current.selection.sentence ?: paragraphs.getOrNull(index)?.let { ReadingPlanner.sentences(it).firstOrNull() }
        mutableReading.update { it.copy(selection = ReadingSelection(scope, index, sentence), selectedSession = null) }
        rebuildReadingPreview()
    }
    override fun chooseParagraph(index: Int) {
        if (state.value.busy) return
        val paragraph = reading.value.input?.paragraphs?.getOrNull(index) ?: return
        mutableReading.update { it.copy(selection = it.selection.copy(paragraph = index, sentence = ReadingPlanner.sentences(paragraph).firstOrNull()), selectedSession = null) }
        rebuildReadingPreview()
    }
    override fun chooseSentence(sentence: String) {
        if (state.value.busy) return
        mutableReading.update { it.copy(selection = it.selection.copy(sentence = sentence), selectedSession = null) }
        rebuildReadingPreview()
    }
    private fun selectedPlan(): ReadingPlan {
        val current = reading.value
        val input = current.input ?: error("请粘贴英文内容或从文章发起朗读")
        return ReadingPlanner.create(input.copy(selection = current.selection))
    }
    private fun rebuildReadingPreview() {
        stopPlayback(); mutable.update { it.copy(selected = null) }
        val result = runCatching { selectedPlan() }
        mutableReading.update { it.copy(rows = result.getOrNull()?.items?.map { item -> ReadingRow(item.label, item.text.trim().take(160)) }.orEmpty(),
            requestError = if (it.text.isBlank()) "" else result.exceptionOrNull()?.message.orEmpty()) }
    }
    override fun generateReading(reuse: Boolean) {
        if (state.value.busy) return
        val plan = runCatching { selectedPlan() }.getOrElse { error -> mutableReading.update { it.copy(requestError = error.message.orEmpty()) }; return }
        val config = runCatching { mutableForm.value.parse() }.getOrElse { error -> mutable.update { it.copy(message = "参数无效：${error.message}") }; return }
        stopPlayback()
        mutableReading.update { it.copy(selectedSession = null, rows = plan.items.map { item -> ReadingRow(item.label, item.text.trim().take(160)) }) }
        work("准备朗读队列；${plan.items.size}个片段") { token ->
            check(supportedAbi) { "语音生成要求arm64-v8a设备" }
            val saved = ReadingQueue(service, runs, metrics::nowNanos).generate(plan, config, token, reuse) { update ->
                mutableReading.update { old -> old.copy(rows = old.rows.mapIndexed { i, row -> if (i == update.item.index)
                    row.copy(status = update.state, runId = update.run?.id, hasAudio = update.run?.report?.has("audio") == true) else row }) }
                if (update.run != null) mutable.update { it.copy(selected = update.run) }
            }
            mutableReading.update { it.copy(selectedSession = saved, rows = readingRows(saved)) }
            mutable.update { it.copy(selected = saved, message = "${runStatusTitle(saved.status)} · 已完成 ${saved.report.optInt("completedCount")}/${plan.items.size}片段") }
        }
    }
    private fun readingRows(run: SavedRun): List<ReadingRow> {
        val items = run.report.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i); val status = item.getString("status")
            ReadingRow(item.getString("label"), status = status, runId = if (item.isNull("runId")) null else item.getString("runId"),
                hasAudio = status in setOf("success", "cache", "token_limit"))
        }
    }
    override fun selectReadingHistory(id: String) {
        stopPlayback()
        work("读取保存的朗读") {
            val run = runs.read(id)
            mutable.update { it.copy(selected = run, message = "已选择保存的音频，播放和导出绑定此结果") }
            mutableReading.update { it.copy(selectedSession = run, rows = readingRows(run)) }
        }
    }
    fun pageStopped() {
        stopPlayback(); releaseAfterExit = true
        if (state.value.busy) cancel() else onWorkerIdle()
    }
    override fun onCleared() {
        disposed = true; ticker.shutdownNow(); stopPlayback()
        runner.closeAfterExit { runCatching { engine.unload() } }
    }
}
