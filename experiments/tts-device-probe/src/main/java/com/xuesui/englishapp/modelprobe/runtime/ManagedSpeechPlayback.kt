package com.xuesui.englishapp.modelprobe.runtime

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xuesui.englishapp.modelprobe.core.storage.RunStore
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class GlobalPlayback(
    val targetKey: String? = null, val paragraph: Int? = null, val desired: Boolean = false,
    val waiting: Boolean = false, val position: Long = 0, val duration: Long = 0,
    val speed: Float = 1f, val label: String = "未播放", val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val title: String = "尚未选择音频", val selection: PlayAvailability? = null,
    val sequence: List<Int> = emptyList(), val runId: String? = null,
)

@androidx.annotation.OptIn(UnstableApi::class)
internal class ManagedSpeechPlayback(private val app: Context, private val changed: (PlaybackState) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(GlobalPlayback())
    val state = mutable.asStateFlow()
    var epoch = 0L; private set
    fun newSelection(): Long {
        if (intent != null) pause() else { epoch++; loading = false }
        return epoch
    }
    private var host: SpeechMediaService? = null
    private var output: ExoPlayer? = null
    private var intent: PlaybackIntent? = null
    private var available: PlayAvailability? = null
    private var legacy: Triple<File, String, Closeable?>? = null
    private val leases = mutableListOf<Closeable>()
    private val pins = mutableSetOf<String>()
    private var loaded: String? = null
    private var loading = false
    private var referenceWorkStarted = false
    private var speed = 1f
    private var legacyTitle = "保存的音频"
    private val runtime get() = SpeechRuntimeRegistry.get(app).background
    private val progress = object : Runnable {
        override fun run() {
            output?.let { mutable.value = mutable.value.copy(position = if (loaded == null) 0 else it.currentPosition.coerceAtLeast(0), duration = if (loaded == null) 0 else it.duration.coerceAtLeast(0)) }
            if (host != null) main.postDelayed(this, 500)
        }
    }
    fun playRange(value: PlayAvailability, existingOnly: Boolean, startParagraph: Int? = null) {
        stop()
        available = value
        val indexes = if (existingOnly) value.indexes.filter { it in value.audio } else value.indexes
        if (indexes.isEmpty()) { publish(PlaybackPhase.FAILED, "没有可播放的自然段"); return }
        intent = PlaybackIntent(key(value.target), indexes)
        if (startParagraph != null && !intent!!.select(startParagraph, true)) { stop(); return }
        publish(PlaybackPhase.PREPARING, if (existingOnly) "仅播放已有内容，缺段将跳过" else "按原段顺序播放")
        startOutput()
    }
    fun play(file: File, runId: String, sourceLease: Closeable? = null, title: String = "保存的音频") {
        stop(); legacyTitle = title; legacy = Triple(file, runId, sourceLease)
        intent = PlaybackIntent("legacy:$runId", listOf(0)); publish(PlaybackPhase.PREPARING, "准备播放保存的音频")
        startOutput()
    }
    private fun startOutput() {
        try {
            app.startService(Intent(app, SpeechMediaService::class.java))
            if (host != null) loadCurrent()
        } catch (failure: RuntimeException) { fail("系统未允许播放服务启动：${failure.message}") }
    }
    internal fun attach(service: SpeechMediaService, player: ExoPlayer) {
        host = service; output = player
        player.setPlaybackSpeed(speed)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (output !== player) return
                if (playbackState == Player.STATE_READY && loaded != null) publish(if (intent?.desired == true) PlaybackPhase.PLAYING else PlaybackPhase.PAUSED)
                if (playbackState == Player.STATE_ENDED && loaded != null) {
                    intent?.ended(); loaded = null
                    if (intent?.paragraph == null) { releaseOutput(); publish(PlaybackPhase.COMPLETED, "播放完毕") }
                    else loadCurrent()
                }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (output !== player) return
                if (!playWhenReady && reason in setOf(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) pause()
            }
            override fun onPlayerError(error: PlaybackException) { if (output === player) fail("播放失败：${error.message}") }
            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                if (output === player) { speed = parameters.speed; publishSpeed() }
            }
        })
        main.removeCallbacks(progress); main.post(progress)
        loadCurrent()
    }
    private fun publish(phase: PlaybackPhase, message: String? = null, waiting: Boolean = false) {
        val range = intent
        val paragraph = if (available != null) range?.paragraph else null
        val label = message ?: when {
            waiting -> "等待第${(paragraph ?: 0) + 1}段生成，未跳过缺段"
            phase == PlaybackPhase.PAUSED -> "播放已暂停"
            paragraph != null -> "正在播放第${paragraph + 1}段 · ${speed}×"
            else -> "播放保存的音频 · ${speed}×"
        }
        mutable.value = mutable.value.copy(targetKey = range?.key, paragraph = paragraph, desired = range?.desired == true, waiting = waiting,
            speed = speed, label = label, phase = phase, selection = available, sequence = range?.indexes.orEmpty(),
            title = available?.target?.plan?.input?.title?.ifBlank { "未命名文章" } ?: legacyTitle, runId = loaded,
            position = if (loaded == null) 0 else mutable.value.position, duration = if (loaded == null) 0 else mutable.value.duration)
        changed(PlaybackState(phase, loaded, speed, if (phase == PlaybackPhase.FAILED) label else null))
    }
    private fun loadCurrent() {
        val player = output ?: return
        val range = intent ?: return
        if (!range.desired || loading) return
        val index = range.paragraph ?: return
        if (loaded != null) { player.play(); publish(PlaybackPhase.PLAYING); return }
        legacy?.let { value ->
            val ticket = epoch
            referenceWorkStarted = true; loading = true
            runtime.io("保存播放引用") {
                val result = runCatching { runtime.owner.runs.durablePins("player", setOf(value.second), union = true) }
                main.post {
                    if (ticket == epoch) loading = false
                    if (ticket == epoch && intent === range && range.desired && output === player)
                        result.fold(onSuccess = {
                            legacy = null; value.third?.let { leases += it }; pins += value.second
                            prepare(player, value.first, value.second, "保存的语音")
                        }, onFailure = { fail("无法保护播放文件：${it.message}") })
                }
            }
            return
        }
        val selection = available ?: return
        val id = selection.audio[index]
        if (!range.ready(selection.audio.keys) || id == null) { player.pause(); publish(PlaybackPhase.PREPARING, waiting = true); return }
        val ticket = epoch
        val heldPins = pins.toSet()
        loading = true
        referenceWorkStarted = true
        runtime.io("校验播放音频") {
            val result = runCatching {
                runtime.owner.runs.durablePins("player", selection.audio.values.toSet() + heldPins, union = true)
                runtime.owner.runs.acquire(id)
            }
            main.post {
                if (ticket == epoch) loading = false
                if (ticket != epoch || !range.desired || intent !== range || output !== player) { result.getOrNull()?.close(); return@post }
                result.fold(onSuccess = { lease ->
                    leases += lease; pins += id
                    prepare(player, lease.run.artifact("audio"), id, "${selection.target.plan.input.title} · 第${index + 1}段")
                }, onFailure = { fail("音频校验失败：${it.message}；请重新生成此段") })
            }
        }
    }
    private fun prepare(player: ExoPlayer, file: File, id: String, title: String) {
        loaded = id
        mutable.value = mutable.value.copy(position = 0, duration = 0)
        player.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(android.net.Uri.fromFile(file))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title.take(220)).build()).build())
        player.prepare(); player.play(); publish(PlaybackPhase.PREPARING)
    }
    fun audioChanged() {
        val range = intent ?: return
        val selection = available ?: return
        val ticket = epoch
        runtime.io("更新待播段落") {
            val found = runtime.availability(selection.target, settingsKey = selection.settingsKey)
            main.post {
                if (ticket == epoch && intent === range) {
                    available = selection.copy(audio = found.audio + selection.audio.filterValues { it in pins })
                    publish(mutable.value.phase, mutable.value.label, mutable.value.waiting)
                    if (range.desired && loaded == null) loadCurrent()
                }
            }
        }
    }
    fun pause() {
        epoch++; intent?.pause(); output?.pause(); loading = false
        publish(PlaybackPhase.PAUSED)
    }
    fun resume() {
        intent?.resume()
        if (output == null) startOutput() else loadCurrent()
        if (loaded == null) { publish(PlaybackPhase.PREPARING, waiting = true); audioChanged() }
    }
    fun seek(position: Long, expectedRun: String? = loaded) {
        if (loaded == null || loaded != expectedRun) return
        val player = output ?: return
        val target = boundedSeek(position, player.duration) ?: return
        player.seekTo(target)
        mutable.value = mutable.value.copy(position = target)
    }
    fun adjacent(direction: Int) {
        require(direction == -1 || direction == 1)
        intent?.adjacent(direction)?.let { selectParagraph(it, play = intent?.desired == true) }
    }
    /** Only navigates the already approved sequence. It never enqueues generation. */
    fun selectParagraph(index: Int, play: Boolean = true) {
        val range = intent ?: return
        if (available == null || !range.select(index, play)) return
        epoch++; loading = false; loaded = null
        output?.pause(); output?.clearMediaItems()
        mutable.value = mutable.value.copy(position = 0, duration = 0)
        publish(if (play) PlaybackPhase.PREPARING else PlaybackPhase.PAUSED)
        if (play) { if (output == null) startOutput() else loadCurrent(); audioChanged() }
    }
    fun setSpeed(value: Float) {
        if (!value.isFinite() || value !in 0.5f..2f) return
        speed = value; output?.setPlaybackSpeed(value); publishSpeed()
    }
    private fun publishSpeed() {
        val current = mutable.value
        publish(current.phase, if (current.phase in setOf(PlaybackPhase.IDLE, PlaybackPhase.COMPLETED, PlaybackPhase.FAILED)) current.label else null, current.waiting)
    }
    private fun releaseOutput() {
        // ExoPlayer.release waits for its internal player teardown before audio references are
        // released; clearing a playlist alone is not used as a file-lifetime fence.
        val referenced = referenceWorkStarted || pins.isNotEmpty() || leases.isNotEmpty()
        host?.resetPlayer()
        leases.forEach { it.close() }; leases.clear()
        legacy?.third?.close(); legacy = null; pins.clear(); loaded = null
        referenceWorkStarted = false
        if (referenced) runtime.io("释放播放引用") { runtime.owner.runs.durablePins("player", emptySet()); runtime.owner.runs.recover() }
    }
    fun stop() {
        epoch++; intent?.pause(); intent = null; available = null; loading = false
        releaseOutput(); mutable.value = GlobalPlayback(speed = speed); changed(PlaybackState(speed = speed))
    }
    private fun fail(message: String) { epoch++; intent?.pause(); releaseOutput(); publish(PlaybackPhase.FAILED, message) }
    internal fun detached(service: SpeechMediaService) {
        if (host !== service) return
        host = null; output = null; main.removeCallbacks(progress)
        epoch++; intent?.pause(); loading = false; leases.forEach { it.close() }; leases.clear(); pins.clear(); loaded = null
        legacy?.third?.close(); legacy = null
        runtime.io("释放播放器引用") { runtime.owner.runs.durablePins("player", emptySet()); runtime.owner.runs.recover() }
        if (state.value.phase != PlaybackPhase.COMPLETED) publish(PlaybackPhase.PAUSED, "播放服务已停止；需手动继续")
    }
    companion object { fun key(target: ReadingTarget) = target.key + ":" + target.bodyKey }
}
