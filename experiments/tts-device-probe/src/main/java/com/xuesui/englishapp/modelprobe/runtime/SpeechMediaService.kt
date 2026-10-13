package com.xuesui.englishapp.modelprobe.runtime

import android.content.Intent
import android.os.Process
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.annotation.OptIn(UnstableApi::class)
class SpeechMediaService : MediaSessionService() {
    private lateinit var runtimePlayer: ManagedSpeechPlayback
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        runtimePlayer = SpeechRuntimeRegistry.get(this).playback
        player = buildPlayer()
        val builder = MediaSession.Builder(this, controlled(player))
        launchPendingIntent(this)?.let(builder::setSessionActivity)
        session = builder.build()
        runtimePlayer.attach(this, player)
    }
    private fun buildPlayer() = ExoPlayer.Builder(this).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        setHandleAudioBecomingNoisy(true)
        setWakeMode(C.WAKE_MODE_LOCAL)
    }
    private fun controlled(delegate: Player): Player = object : ForwardingPlayer(delegate) {
        override fun play() = runtimePlayer.resume()
        override fun pause() = runtimePlayer.pause()
        override fun stop() = runtimePlayer.stop()
        override fun setPlayWhenReady(playWhenReady: Boolean) { if (playWhenReady) runtimePlayer.resume() else runtimePlayer.pause() }
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).remove(Player.COMMAND_SET_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_PREVIOUS).build()
    }
    internal fun resetPlayer() {
        player.release()
        player = buildPlayer()
        session?.setPlayer(controlled(player))
        runtimePlayer.attach(this, player)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.uid == Process.myUid() || controllerInfo.uid == Process.SYSTEM_UID || controllerInfo.isTrusted) session else null
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady) stopSelf() }
    override fun onDestroy() {
        session?.release(); session = null
        player.release()
        runtimePlayer.detached(this)
        super.onDestroy()
    }
}
