package com.xuesui.englishapp.modelprobe.features.speech

import java.io.File

internal enum class PlaybackPhase { IDLE, PREPARING, PLAYING, PAUSED, COMPLETED, FAILED }
internal data class PlaybackState(val phase: PlaybackPhase = PlaybackPhase.IDLE, val runId: String? = null, val speed: Float = 1f, val error: String? = null) {
    val label get() = when (phase) {
        PlaybackPhase.IDLE -> "未播放"
        PlaybackPhase.PREPARING -> "准备播放"
        PlaybackPhase.PLAYING -> "正在播放 · ${speed}×"
        PlaybackPhase.PAUSED -> "已暂停 · 可从当前位置继续"
        PlaybackPhase.COMPLETED -> "播放完毕 · 可重播"
        PlaybackPhase.FAILED -> "播放失败：$error"
    }
}
internal interface AudioSession {
    fun prepare()
    fun play(speed: Float)
    fun pause()
    fun release()
}
internal interface PlaybackEvents {
    fun prepared()
    fun completed()
    fun interrupted()
    fun failed(message: String)
}
internal fun interface AudioFactory { fun create(file: File, events: PlaybackEvents): AudioSession }

/** All methods/callbacks run on the UI thread. Generation fences obsolete preparation callbacks. */
internal class SpeechPlayback(private val factory: AudioFactory, private val changed: (PlaybackState) -> Unit) {
    var state = PlaybackState(); private set
    var epoch = 0L; private set
    private var session: AudioSession? = null
    private var prepared = false
    private var desiredPlaying = true
    private var speed = 1f
    private fun publish(value: PlaybackState) { state = value; changed(value) }
    fun play(file: File, runId: String) {
        stop(); val ticket = epoch
        prepared = false; desiredPlaying = true
        publish(PlaybackState(PlaybackPhase.PREPARING, runId, speed))
        try {
            session = factory.create(file, object : PlaybackEvents {
                override fun prepared() {
                    if (ticket != epoch) return
                    prepared = true
                    if (desiredPlaying) resume() else publish(state.copy(phase = PlaybackPhase.PAUSED))
                }
                override fun completed() {
                    if (ticket != epoch) return
                    release(); publish(state.copy(phase = PlaybackPhase.COMPLETED))
                }
                override fun interrupted() { if (ticket == epoch) pause() }
                override fun failed(message: String) { if (ticket == epoch) failure(message) }
            })
            session!!.prepare()
        } catch (error: Exception) { failure(error.message ?: "播放器初始化失败") }
    }
    fun pause() {
        if (state.phase !in setOf(PlaybackPhase.PREPARING, PlaybackPhase.PLAYING)) return
        desiredPlaying = false
        try { if (prepared) session?.pause(); publish(state.copy(phase = PlaybackPhase.PAUSED)) }
        catch (error: Exception) { failure(error.message ?: "无法暂停") }
    }
    fun resume() {
        if (session == null) return
        desiredPlaying = true
        if (!prepared) { publish(state.copy(phase = PlaybackPhase.PREPARING)); return }
        try { session!!.play(speed); publish(state.copy(phase = PlaybackPhase.PLAYING, speed = speed)) }
        catch (error: Exception) { failure(error.message ?: "当前设备不支持此播放设置") }
    }
    fun setSpeed(value: Float) {
        require(value.isFinite() && value in 0.5f..2f) { "播放倍速应在0.5至2.0之间" }
        speed = value
        if (state.phase == PlaybackPhase.PLAYING) resume()
        else publish(state.copy(speed = speed))
    }
    private fun release() {
        epoch++; prepared = false
        val old = session; session = null
        runCatching { old?.release() }
    }
    fun stop() { release(); publish(PlaybackState(speed = speed)) }
    private fun failure(message: String) { release(); publish(state.copy(phase = PlaybackPhase.FAILED, error = message)) }
}
