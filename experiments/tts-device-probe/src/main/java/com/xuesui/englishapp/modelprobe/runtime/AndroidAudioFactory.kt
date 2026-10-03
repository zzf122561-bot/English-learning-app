package com.xuesui.englishapp.modelprobe.runtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.xuesui.englishapp.modelprobe.features.speech.*
import java.io.File

internal class AndroidAudioFactory(context: Context) : AudioFactory {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    override fun create(file: File, events: PlaybackEvents): AudioSession = object : AudioSession {
        private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        private var released = false
        private var focused = false
        private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { if (!released && it < 0) events.interrupted() }.build()
        private val player = MediaPlayer()
        override fun prepare() {
            player.setAudioAttributes(attributes); player.setDataSource(file.path)
            player.setOnPreparedListener { if (!released) events.prepared() }
            player.setOnCompletionListener { if (!released) events.completed() }
            player.setOnErrorListener { _, what, extra -> if (!released) events.failed("$what / $extra"); true }
            player.prepareAsync()
        }
        override fun play(speed: Float) {
            if (!focused) {
                check(audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "未取得音频焦点，请稍后继续播放" }
                focused = true
            }
            player.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f).setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_FAIL)
            check(kotlin.math.abs(player.playbackParams.speed - speed) < 0.01f) { "当前设备未应用所选倍速" }
            player.start()
        }
        override fun pause() { player.pause(); abandon() }
        private fun abandon() { if (focused) { focused = false; audio.abandonAudioFocusRequest(focus) } }
        override fun release() { released = true; try { player.release() } finally { abandon() } }
    }
}
