package com.xuesui.englishapp.modelprobe.runtime

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.features.speech.WavWriter
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Media3 extractor/seek against a synthetic WAV, without auto-playing sound. This does
 * not test notifications, audio focus, lockscreen survival or native speech inference. */
@RunWith(AndroidJUnit4::class)
class Media3WaveDeviceTest {
    @Test fun savedPcmWavePreparesWithCorrectDurationAndCanSeek() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "media3-wave-fixture-${UUID.randomUUID()}.wav")
        WavWriter(file).use { it.append(FloatArray(48000) { n -> if (n % 2 == 0) 0.01f else -0.01f }, 24000, WorkToken()); it.finish() }
        lateinit var player: ExoPlayer
        val ready = CountDownLatch(1)
        var failure: PlaybackException? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_READY) ready.countDown() }
                    override fun onPlayerError(error: PlaybackException) { failure = error; ready.countDown() }
                })
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); player.prepare()
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); assertNull(failure)
            instrumentation.runOnMainSync {
                assertEquals(2000L, player.duration); assertFalse(player.playWhenReady)
                player.seekTo(1000); assertEquals(1000L, player.currentPosition)
                player.pause(); assertFalse(player.playWhenReady)
            }
        } finally { instrumentation.runOnMainSync { player.release() }; file.delete() }
    }
}
