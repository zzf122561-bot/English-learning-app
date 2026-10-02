package com.xuesui.englishapp.modelprobe.runtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Composition root: only this layer selects the concrete trusted engine and its editor/codec. */
internal class ProbeViewModel(context: Context) : ViewModel(), SpeechActions {
    private val app = context.applicationContext as android.app.Application
    val descriptor = QwenModel.descriptor
    val supportedAbi = Build.SUPPORTED_ABIS.contains(descriptor.abi)
    private val mutable = MutableStateFlow(SpeechUiState())
    val state = mutable.asStateFlow()
    private val mutableForm = MutableStateFlow(QwenForm.from(QwenConfiguration()))
    val form = mutableForm.asStateFlow()
    private val root by lazy { File(app.noBackupFilesDir, "model-probe-v1").also { it.mkdirs() } }
    private val models by lazy { ModelStore(File(root, "models")) }
    private val runs by lazy { RunStore(File(root, "results"), File(root, "cache-index")) }
    private val settings by lazy { SettingsStore(File(root, "settings")) }
    private val engine: SpeechEngine = QwenSpeechEngine()
    private val metrics = AndroidMetrics(app)
    private val service by lazy { SpeechService(engine, models, runs, metrics) { message -> mutable.update { it.copy(message = message) } } }
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var playbackGeneration = 0L
    private val audio = app.getSystemService(AudioManager::class.java)
    private var sample: String? = null
    private var talker: Uri? = null
    private var tokenizer: Uri? = null
    private val exports = mutableMapOf<String, String>()
    @Volatile private var disposed = false
    @Volatile private var started = 0L
    private val runner = TaskRunner { busy, cancelling ->
        mutable.update { it.copy(busy = busy, cancelling = cancelling, phase = if (busy) it.phase else "", progress = if (busy) it.progress else null) }
    }
    private val ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "probe-progress").also { it.isDaemon = true } }
    init {
        ticker.scheduleWithFixedDelay({
            if (state.value.busy) mutable.update { it.copy(phase = engine.phase(), elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000_000) }
        }, 500, 500, TimeUnit.MILLISECONDS)
        work("读取样例与模型清单") {
            val sampleResult = runCatching { SpeechText.sample { app.assets.open("probe-sample.txt") } }
            sample = sampleResult.getOrNull()
            val manifest = runCatching { app.assets.open("models/manifest.json").use { ModelCatalog(listOf(descriptor)).parseManifest(it.boundedText(65536)) } }
            var settingMessage = ""
            val (config, preferences) = runCatching {
                settings.load(descriptor.modelId, descriptor.engineVersion, 2)?.let {
                    QwenConfiguration.fromJson(it.getJSONObject("generation")) to SpeechPreferences.fromJson(it.getJSONObject("speech"))
                } ?: (QwenConfiguration() to SpeechPreferences())
            }.getOrElse { settingMessage = "设置读取失败，已恢复默认，原文件保留：${it.message}"; QwenConfiguration() to SpeechPreferences() }
            mutableForm.value = QwenForm.from(config)
            mutable.update { it.copy(text = sample.orEmpty(), sampleAvailable = sample != null,
                sampleMessage = if (sample != null) "已读入宿主固定样例，可编辑或恢复" else "样例缺失或无效：${sampleResult.exceptionOrNull()?.message}；可手动输入",
                manifestReady = manifest.isSuccess, manifestMessage = if (manifest.isSuccess) "内置模型清单身份匹配；安装时校验完整副本" else "内置模型清单不可用：${manifest.exceptionOrNull()?.message}",
                mode = preferences.mode, batchCount = preferences.batchCount, playbackSpeed = preferences.playbackSpeed,
                cacheAllowed = !config.randomSeed, message = settingMessage.ifBlank { "实验性设备验证候选；请先安装并载入模型" }) }
        }
    }
    private fun refresh() {
        val presence = runCatching { models.present(descriptor) }
        val installed = presence.getOrDefault(false)
        val history = runs.history()
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
        if (speed !in 0.5f..2f) return
        mutable.update { it.copy(playbackSpeed = speed) }
        runCatching { player?.let { p -> if (p.isPlaying) p.playbackParams = p.playbackParams.setSpeed(speed) } }
    }
    override fun play(id: String) {
        stopPlayback()
        val generation = playbackGeneration
        work("核对所选音频") { token ->
            val run = runs.read(id)
            val file = run.artifact("audio")
            token.check()
            main.post { if (!disposed && generation == playbackGeneration && !token.cancelled.get()) startPlayback(file, id) }
        }
    }
    private fun startPlayback(file: File, id: String) {
        stopPlayback()
        try {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change -> if (change < 0) stopPlayback() }.build()
            check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "未取得音频焦点" }
            focus = request
            val p = MediaPlayer(); player = p
            p.setAudioAttributes(attributes); p.setDataSource(file.path)
            p.setOnPreparedListener {
                if (player === it && !disposed) {
                    it.playbackParams = it.playbackParams.setSpeed(state.value.playbackSpeed); it.start()
                    mutable.update { s -> s.copy(playback = "播放结果 ${id.take(8)} · ${s.playbackSpeed}×") }
                }
            }
            p.setOnCompletionListener { if (player === it) stopPlayback() }
            p.setOnErrorListener { source, what, extra ->
                if (player === source) { stopPlayback(); mutable.update { it.copy(playback = "播放失败：$what / $extra") } }
                true
            }
            p.prepareAsync()
        } catch (failure: Exception) { stopPlayback(); mutable.update { it.copy(playback = "播放失败：${failure.message}") } }
    }
    override fun stopPlayback() {
        playbackGeneration++
        player?.release(); player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
        mutable.update { it.copy(playback = "未播放") }
    }
    fun prepareExport(role: String): Boolean {
        val id = state.value.selected?.id ?: return false
        exports[role] = id; return true
    }
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
    fun pageStopped() { stopPlayback(); if (state.value.busy) cancel() }
    override fun onCleared() {
        disposed = true; ticker.shutdownNow(); stopPlayback()
        runner.closeAfterExit { runCatching { engine.unload() } }
    }
}
