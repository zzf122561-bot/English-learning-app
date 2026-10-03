package com.xuesui.englishapp.modelprobe.runtime

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.core.storage.removeOwned
import com.xuesui.englishapp.modelprobe.features.speech.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic cache-directory fixtures only. These tests must be run by the manager's device host. */
@RunWith(AndroidJUnit4::class)
class ReadingRuntimeDeviceTest {
    @Test fun formalReaderDoesNotRequireSampleAndKeepsSameActivityRoutesSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "reading-runtime-${UUID.randomUUID()}").also { it.mkdir() }
        val owner = ViewModelStore()
        lateinit var vm: ProbeViewModel
        try {
            instrumentation.runOnMainSync { vm = ProbeViewModel(context, diagnostic = false, privateDirectory = root); owner.put("fixture", vm) }
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (vm.state.value.busy && System.nanoTime() < until) Thread.sleep(20)
            assertFalse(vm.state.value.busy)
            instrumentation.runOnMainSync {
                vm.enterReading(null)
                assertEquals("", vm.reading.value.text)
                assertFalse(vm.state.value.sampleAvailable)
                assertEquals("", vm.state.value.sampleMessage)
                vm.editReadingText("My own draft.")
                val request = SpeechReadingRequest("article-one", "Article", listOf("First sentence.", "Second sentence."), SpeechReadingScope.PARAGRAPH, 1)
                vm.enterReading(request)
                assertEquals(1, vm.reading.value.selection.paragraph)
                assertEquals(1, vm.reading.value.rows.size)
                vm.enterReading(request.copy(englishParagraphs = listOf("Unwanted duplicate.")))
                assertEquals("First sentence.\n\nSecond sentence.", vm.reading.value.text)
                assertFalse(vm.state.value.busy) // Arrival/recomposition never starts inference.
                vm.enterReading(null)
                assertEquals("My own draft.", vm.reading.value.text)
                vm.enterReading(request.copy(requestId = "article-two"))
                assertEquals("Article", vm.reading.value.title)
                assertFalse(vm.state.value.loaded)
            }
        } finally {
            instrumentation.runOnMainSync { owner.clear() }
            removeOwned(context.cacheDir, root)
        }
    }
    @Test fun platformPlaybackCanPreparePauseResumeAndReplaySyntheticWave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "reading-audio-${UUID.randomUUID()}").also { it.mkdir() }
        val wave = File(root, "silence.wav")
        WavWriter(wave).use { it.append(FloatArray(48000), 24000, WorkToken()); it.finish() }
        val playing = CountDownLatch(1)
        lateinit var playback: SpeechPlayback
        try {
            instrumentation.runOnMainSync {
                playback = SpeechPlayback(AndroidAudioFactory(context)) { if (it.phase == PlaybackPhase.PLAYING) playing.countDown() }
                playback.play(wave, "synthetic-audio")
            }
            assertTrue("Android MediaPlayer did not start", playing.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                playback.pause(); assertEquals(PlaybackPhase.PAUSED, playback.state.phase)
                playback.setSpeed(0.75f); playback.resume(); assertEquals(PlaybackPhase.PLAYING, playback.state.phase)
                playback.stop(); assertEquals(PlaybackPhase.IDLE, playback.state.phase)
                playback.play(wave, "synthetic-audio"); assertEquals(PlaybackPhase.PREPARING, playback.state.phase)
            }
        } finally {
            instrumentation.runOnMainSync { playback.stop() }
            removeOwned(context.cacheDir, root)
        }
    }
}
