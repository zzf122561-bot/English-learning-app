package com.xuesui.englishapp.modelprobe.runtime

import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.core.storage.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic engine/files. Compiling this source is not a device or native inference result. */
@RunWith(AndroidJUnit4::class)
class InlineRuntimeDeviceTest {
    private class Fixture : AutoCloseable {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "inline-fixture-${UUID.randomUUID()}").also { check(it.mkdir()) }
        val bytes = "synthetic model fixture".toByteArray()
        val descriptor = ModelDescriptor("fixture", "fake", "test-only", "speech.synthesis.v1", "1", "test", "Synthetic",
            "fake", Build.SUPPORTED_ABIS.first(), emptySet(), listOf(ModelFile("talker", "tiny.gguf", "models/tiny.gguf", bytes.size.toLong(), bytes.sha256())))
        val engine = Engine(descriptor)
        private val owner = ViewModelStore()
        lateinit var vm: ProbeViewModel
        init {
            ModelStore(File(root, "models")) { Long.MAX_VALUE }.install(descriptor, WorkToken(), { bytes.inputStream() }) { _, _ -> }
            main { vm = ProbeViewModel(context, false, root, engine); owner.put("fixture", vm) }
            idle()
        }
        fun main(body: () -> Unit) = instrumentation.runOnMainSync(body)
        fun idle() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            do {
                instrumentation.waitForIdleSync()
                if (!vm.state.value.busy) {
                    Thread.sleep(30); instrumentation.waitForIdleSync()
                    if (!vm.state.value.busy) return
                }
                Thread.sleep(10)
            } while (System.nanoTime() < deadline)
            fail("Synthetic worker did not exit")
        }
        override fun close() {
            engine.allowExit?.countDown()
            main { vm.leaveInline() }; idle()
            main { owner.clear() }
            removeOwned(context.cacheDir, root)
        }
    }
    private class Engine(override val descriptor: ModelDescriptor) : SpeechEngine {
        @Volatile override var loaded = false
        override val instructionSupported = true
        override val speakers = listOf("ryan", "aiden")
        var lease: ModelLease? = null
        val calls = AtomicInteger()
        @Volatile var started: CountDownLatch? = null
        @Volatile var allowExit: CountDownLatch? = null
        @Volatile var limit = false
        override fun load(lease: ModelLease, threads: Int, token: WorkToken): Long { this.lease = lease; loaded = true; return 1 }
        override fun synthesize(text: String, parameters: SpeechParameters, token: WorkToken): SpeechAudio {
            calls.incrementAndGet(); started?.countDown()
            allowExit?.let { check(it.await(10, TimeUnit.SECONDS)) }
            token.check()
            return SpeechAudio(FloatArray(240) { if (it % 2 == 0) 0.1f else -0.1f }, 24000, parameters, 0, 1, 0, 1, limit)
        }
        override fun unload() { loaded = false; lease?.close(); lease = null }
        override fun phase() = "synthetic"
        override fun selfCheck(token: WorkToken) = "synthetic"
    }
    private fun request(id: String = "request", paragraph: Int = 0) = SpeechReadingRequest(id, "Fixture article",
        listOf("First paragraph.", "Second paragraph."), SpeechReadingScope.PARAGRAPH, paragraph)

    @Test fun settingsAndInlineReuseParametersButOnlyExplicitGenerationStartsWork() = Fixture().use { f ->
        val request = request()
        f.main { f.vm.enterInline("word-memory:one", request) }; f.idle()
        f.main { f.vm.enterInline("word-memory:one", request) }; f.idle()
        assertEquals(0, f.engine.calls.get())
        f.main { f.vm.enterSettings(); f.vm.editForm(f.vm.form.value.copy(speaker = "aiden", instruction = "Clear and calm")) }; f.idle()
        f.main { f.vm.enterInline("word-memory:one", request) }; f.idle()
        assertEquals("aiden", f.vm.form.value.speaker)
        assertEquals(0, f.engine.calls.get())
        f.main { f.vm.generateInline() }; f.idle()
        val saved = f.vm.inline.value.latest!!
        assertTrue(f.vm.inline.value.matchesSettings)
        assertEquals("aiden", saved.report.getJSONObject("requested").getString("speaker"))
        f.main { f.vm.enterInline("word-memory:one", request.copy(requestId = "stable-new-selection-id")) }; f.idle()
        assertEquals(1, f.engine.calls.get()); assertEquals(saved.id, f.vm.inline.value.latest!!.id)
        f.main { f.vm.editForm(f.vm.form.value.copy(seed = "99")) }
        assertFalse(f.vm.inline.value.matchesSettings)
        f.main { f.vm.leaveInline() }; f.idle(); assertFalse(f.engine.loaded)
    }
    @Test fun selectionSwitchWaitsForRealWorkerExitAndNeverStartsNewRangeAutomatically() = Fixture().use { f ->
        f.main { f.vm.enterInline("word-memory:one", request()) }; f.idle()
        f.engine.started = CountDownLatch(1); f.engine.allowExit = CountDownLatch(1)
        f.main { f.vm.generateInline() }
        assertTrue(f.engine.started!!.await(10, TimeUnit.SECONDS))
        f.main { f.vm.enterInline("word-memory:one", request("next-range", 1)) }
        assertTrue(f.vm.state.value.busy); assertTrue(f.vm.state.value.cancelling)
        assertTrue(f.engine.loaded); assertNull(f.vm.inline.value.latest)
        assertEquals(1, f.vm.inline.value.target!!.plan.input.selection.paragraph)
        f.engine.allowExit!!.countDown(); f.idle()
        assertEquals(1, f.engine.calls.get()); assertFalse(f.vm.inline.value.switching)
        assertNull(f.vm.inline.value.latest); assertFalse(f.engine.loaded)
    }
    @Test fun invalidNewRangeClearsOldSelectionAndNeverFallsBackToPreviousAudio() = Fixture().use { f ->
        f.main { f.vm.enterInline("word-memory:one", request()); }; f.idle()
        f.main { f.vm.generateInline() }; f.idle()
        assertNotNull(f.vm.inline.value.latest)
        f.main { f.vm.enterInline("word-memory:two", request("invalid", 999)); f.vm.playInline() }
        assertNull(f.vm.inline.value.target); assertNull(f.vm.inline.value.latest)
        assertTrue(f.vm.inline.value.error.isNotBlank()); assertEquals(1, f.engine.calls.get())
    }
    @Test fun failedFullThenParagraph001UnlocksGeneratesMatchesAndStartsPlayback() = Fixture().use { f ->
        val full = request().copy(scope = SpeechReadingScope.FULL, paragraphIndex = null)
        f.main { f.vm.enterInline("word-memory:one", full) }; f.idle()
        f.engine.limit = true
        f.main { f.vm.generateInline() }; f.idle()
        assertFalse(f.vm.state.value.busy); assertFalse(f.vm.inline.value.switching)
        assertNull(f.vm.inline.value.latest); assertFalse(f.vm.inline.value.matchesSettings)
        assertEquals("token_limit", f.vm.state.value.history.first().status)
        f.engine.limit = false
        f.main { f.vm.enterInline("word-memory:one", request("paragraph-001")) }; f.idle()
        f.main { f.vm.generateInline() }; f.idle()
        val saved = f.vm.inline.value.latest!!
        assertEquals("PARAGRAPH", saved.report.getString("scope"))
        assertEquals(1, saved.report.getInt("completedCount"))
        assertTrue(f.vm.inline.value.matchesSettings); assertFalse(f.vm.inline.value.switching)
        f.main { f.vm.playInline() }; f.idle()
        assertEquals(saved.id, f.vm.reading.value.playback.runId)
        assertNotEquals(com.xuesui.englishapp.modelprobe.features.speech.PlaybackPhase.FAILED, f.vm.reading.value.playback.phase)
    }
    @Test fun identicalRangeReselectionDoesNotOccupyWorkerAndOtherBadParagraphDoesNotBlock() = Fixture().use { f ->
        val selected = request().copy(englishParagraphs = listOf("A valid paragraph zero one.", "中文备注", ""))
        f.main { f.vm.enterInline("word-memory:one", selected) }; f.idle()
        f.main {
            f.vm.enterInline("word-memory:one", selected.copy(requestId = "same-range-new-id"))
            assertFalse(f.vm.state.value.busy)
            f.vm.generateInline()
        }
        f.idle()
        assertEquals(1, f.engine.calls.get()); assertNotNull(f.vm.inline.value.latest)
        assertTrue(f.vm.inline.value.matchesSettings)
    }
    @Test fun androidPrivateStorageRejectsSymlinkDeletionWithoutTouchingTarget() = Fixture().use { f ->
        val runs = RunStore(File(f.root, "results"), File(f.root, "cache-index"))
        val saved = runs.begin().use { it.commit(org.json.JSONObject().put("taskId", "speech.synthesis.v1").put("status", "failed"), emptyMap()) }
        val external = File(f.root, "keep.txt").also { it.writeText("keep") }
        val link = File(saved.directory, "link.txt")
        Files.createSymbolicLink(link.toPath(), external.toPath())
        try {
            var rejected = false
            try { runs.delete(saved.id) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertEquals("keep", external.readText()); assertTrue(saved.directory.exists())
        } finally { Files.deleteIfExists(link.toPath()) }
    }
}
