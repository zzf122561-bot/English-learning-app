package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import com.xuesui.englishapp.modelprobe.stage2.*
import java.io.File
import java.io.Closeable
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Test

class ReadingRetentionTest {
    private val root = Files.createTempDirectory("reading-retention-").toFile()
    private val models = ModelStore(File(root, "models")) { Long.MAX_VALUE }
    private val descriptor = tinyDescriptor()
    private val engine = FakeSpeechEngine(descriptor)
    private var crashAt: String? = null
    private var fullDisk = false
    private var reclaimFailure = false
    private val runs = RunStore(File(root, "results"), File(root, "cache")) {
        if (it == crashAt) throw PowerLoss()
        if (it == "before_activate" && fullDisk) throw java.io.IOException("Synthetic no space for index")
        if (it == "before_remove" && reclaimFailure) throw java.io.IOException("Synthetic file busy")
    }
    private val service = SpeechService(engine, models, runs, FakeMetrics()) { }
    private val queue = ReadingQueue(service, runs)
    private val params = QwenConfiguration(QwenNativeParameters(threads = 1))
    private class PowerLoss : Error("synthetic process death")
    init { install(models, descriptor) }
    @After fun cleanup() { engine.unload(); root.deleteRecursively() }
    private fun target(document: String = "word-memory:one", paragraph: Int = 0,
        scope: ReadingScope = ReadingScope.PARAGRAPH, text: List<String> = listOf("A clear sentence.", "A clear sentence."),
        title: String = "Same title", sentence: String? = null): ReadingTarget = ReadingTarget(document,
        ReadingPlanner.create(ReadingInput(UUID.randomUUID().toString(), title, text, ReadingSelection(scope, paragraph, sentence))))
    private fun generate(target: ReadingTarget, configuration: QwenConfiguration = params, reuse: Boolean = false) =
        queue.generate(target.plan, configuration, WorkToken(), reuse, target) { }
    private fun children(run: SavedRun): List<String> = run.report.getJSONArray("childRunIds").let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test fun latestRangeReplacesAcrossVoiceSeedTitleAndRequestButNotDocumentOrParagraph() {
        val firstTarget = target()
        val first = generate(firstTarget)
        val changed = params.copy(native = params.native.copy(speaker = "aiden", instruction = "Warm and clear", seed = 77))
        val secondTarget = target(title = "Renamed")
        assertEquals(firstTarget.key, secondTarget.key)
        val second = generate(secondTarget, changed)
        assertEquals(second.id, runs.latest(firstTarget.key)!!.id)
        assertFalse(first.directory.exists()); children(first).forEach { assertFalse(File(root, "results/$it").exists()) }
        assertEquals(2, runs.history(Int.MAX_VALUE).size)
        val otherDoc = generate(target(document = "word-memory:two"), changed, true)
        val otherParagraph = generate(target(paragraph = 1), changed, true)
        assertEquals(3, ReadingHistory.read(runs).size)
        assertTrue(second.directory.exists()); assertTrue(otherDoc.directory.exists()); assertTrue(otherParagraph.directory.exists())
    }
    @Test fun bodyAndParameterValidationStaySeparateFromOpaqueRetentionIdentity() {
        val original = target(document = "../opaque:document/id")
        val saved = generate(original)
        val changedText = target(document = original.documentId, text = listOf("An edited sentence.", "A clear sentence."))
        assertEquals(original.key, changedText.key)
        assertFalse(changedText.matches(saved, params, descriptor.fingerprint, descriptor.engineVersion))
        assertFalse(original.matches(saved, params.copy(native = params.native.copy(seed = 43)), descriptor.fingerprint, descriptor.engineVersion))
        assertFalse(original.matches(saved, params, descriptor.fingerprint, "changed-engine"))
        assertTrue(target(document = original.documentId, title = "New title").matches(saved, params, descriptor.fingerprint, descriptor.engineVersion))
        val updated = generate(changedText)
        assertFalse(saved.directory.exists()); assertTrue(updated.directory.exists())
        assertEquals(root.canonicalFile, File(root, "results").canonicalFile.parentFile)
    }
    @Test fun sentenceSelectionUsesDocumentParagraphAndLogicalSentencePosition() {
        val text = listOf("First sentence. Second sentence.")
        val first = target(scope = ReadingScope.SENTENCE, text = text, sentence = "First sentence.")
        val second = target(scope = ReadingScope.SENTENCE, text = text, sentence = "Second sentence.")
        assertNotEquals(first.key, second.key)
        assertEquals(first.key, target(scope = ReadingScope.SENTENCE, text = listOf("Edited sentence. Second sentence."), sentence = "Edited sentence.").key)
        val repeated = target(scope = ReadingScope.SENTENCE, text = listOf("Again. Again."), sentence = "Again.")
        assertEquals("第1段 · 单句（重复文本）", repeated.label)
        assertEquals("unspecified", repeated.snapshot().getString("occurrence"))
    }
    @Test fun failuresCancellationAndTokenLimitKeepPreviousSuccessUntilNextCompleteCommit() {
        val target = target(scope = ReadingScope.FULL)
        val previous = generate(target)
        engine.action = { error("synthetic inference failure") }
        assertEquals("failed", generate(target).status)
        assertEquals(previous.id, runs.latest(target.key)!!.id)
        engine.action = null; engine.limit = true
        assertEquals("token_limit", generate(target).status)
        assertEquals(previous.id, runs.latest(target.key)!!.id)
        engine.limit = false
        val token = WorkToken()
        val cancelled = queue.generate(target.plan, params, token, false, target) { if (it.state == "success") token.cancel() }
        assertEquals("cancelled", cancelled.status)
        assertEquals(previous.id, runs.latest(target.key)!!.id); assertTrue(previous.artifact("audio").isFile)
        assertEquals(4, ReadingHistory.read(runs).size)
        val finished = generate(target)
        assertEquals(finished.id, runs.latest(target.key)!!.id)
        assertEquals(1, ReadingHistory.read(runs).size)
        assertEquals(3, runs.history(Int.MAX_VALUE).size)
    }
    @Test fun reusedFragmentsAreSharedAndOnlyCollectedAfterLastParentDisappears() {
        val first = generate(target())
        val second = generate(target(document = "word-memory:other"), reuse = true)
        assertEquals(1, engine.calls)
        assertEquals(children(first), children(second))
        assertEquals("cache", second.report.getJSONArray("items").getJSONObject(0).getString("status"))
        val fragment = runs.read(children(first).single()).directory
        val exported = File(root, "user-export.wav").also { it.writeText("user owned") }
        assertTrue(runs.delete(first.id)); assertTrue(fragment.exists())
        assertEquals(1, ReadingHistory.read(runs).size)
        assertTrue(runs.read(second.id).artifact("audio").isFile)
        assertTrue(runs.delete(second.id)); assertFalse(fragment.exists())
        assertTrue(File(root, "cache").listFiles().orEmpty().isEmpty())
        assertTrue(models.present(descriptor)); assertEquals("user owned", exported.readText())
    }
    @Test fun legacyProjectsAreNotGuessedByTitleAndStandaloneCacheIsNotAutoDeleted() {
        val target = target()
        val standalone = service.run(SpeechRequest(target.plan.items.single().text, params, SpeechMode.PARAGRAPH, RunKind.COLD), WorkToken())
        val legacy = queue.generate(target.plan, params, WorkToken(), true) { }
        val current = generate(target, reuse = true)
        assertEquals(listOf(standalone.id), children(current))
        assertTrue(legacy.directory.exists()); assertTrue(standalone.directory.exists())
        assertTrue(runs.delete(current.id))
        assertTrue(standalone.directory.exists()); assertTrue(legacy.directory.exists())
        assertTrue(runs.delete(legacy.id)); children(legacy).forEach { assertFalse(File(root, "results/$it").exists()) }
        assertTrue(standalone.directory.exists())
        assertTrue(runs.delete(standalone.id)); assertTrue(ReadingHistory.read(runs).isEmpty())
    }
    @Test fun audioManagementIncludesOldProjectsBeyondTheDiagnosticHundredRowLimit() {
        repeat(105) { i -> runs.begin().use { it.commit(JSONObject().put("taskId", "speech.synthesis.v1")
            .put("status", "failed").put("createdAtEpochMillis", i.toLong()), emptyMap()) } }
        assertEquals(100, runs.history().size)
        val all = ReadingHistory.read(runs, Int.MAX_VALUE)
        assertEquals(105, all.size)
        assertTrue(runs.delete(all.last().id)); assertEquals(104, ReadingHistory.read(runs, Int.MAX_VALUE).size)
    }
    private fun interruptedPublication(point: String, activated: Boolean) {
        val target = target()
        val previous = generate(target)
        crashAt = point
        try { generate(target, params.copy(native = params.native.copy(seed = 19))); fail("Expected interrupted save") } catch (_: PowerLoss) { }
        if (activated) assertNotEquals(previous.id, runs.latest(target.key)!!.id) else assertEquals(previous.id, runs.latest(target.key)!!.id)
        assertTrue(previous.directory.exists())
        val recovered = RunStore(File(root, "results"), File(root, "cache"))
        val latest = recovered.latest(target.key)!!
        assertNotEquals(previous.id, latest.id); recovered.read(latest.id)
        assertFalse(previous.directory.exists())
        assertEquals(2, recovered.history(Int.MAX_VALUE).size)
        recovered.recover(); assertEquals(2, recovered.history(Int.MAX_VALUE).size)
    }
    @Test fun processDeathAfterDirectoryCommitFinishesPublicationOnRecovery() = interruptedPublication("after_commit", false)
    @Test fun indexWriteFailureBeforeActivationDoesNotDeleteOldAudio() = interruptedPublication("before_activate", false)
    @Test fun processDeathAfterActivationRetriesOldFileReclamation() = interruptedPublication("after_activate", true)
    @Test fun interruptionDuringGarbageCollectionKeepsRetryableIntent() = interruptedPublication("before_remove", true)

    @Test fun noSpaceForLatestIndexKeepsPreviousFilesAndRecoversAfterSpaceReturns() {
        val target = target(); val previous = generate(target)
        fullDisk = true
        rejected { generate(target, params.copy(native = params.native.copy(seed = 11))) }
        assertEquals(previous.id, runs.latest(target.key)!!.id); assertTrue(previous.artifact("audio").exists())
        fullDisk = false; runs.recover()
        assertNotEquals(previous.id, runs.latest(target.key)!!.id); assertFalse(previous.directory.exists())
    }
    @Test fun deferredReclamationDoesNotTurnAnAlreadyPublishedSuccessIntoFailure() {
        val target = target(); val previous = generate(target)
        reclaimFailure = true
        val next = generate(target)
        assertEquals("success", next.status); assertEquals(next.id, runs.latest(target.key)!!.id)
        assertNotNull(runs.maintenanceIssue); assertTrue(previous.directory.exists())
        reclaimFailure = false; runs.recover()
        assertNull(runs.maintenanceIssue); assertFalse(previous.directory.exists())
    }
    @Test fun deathBeforeDirectoryRenameKeepsOldLatestAndLaterReclaimsProvenOrphanFragments() {
        val target = target(); val previous = generate(target)
        crashAt = "before_commit"
        try { generate(target); fail("Expected interruption") } catch (_: PowerLoss) { }
        val reopened = RunStore(File(root, "results"), File(root, "cache"))
        assertEquals(previous.id, reopened.latest(target.key)!!.id); assertTrue(previous.directory.exists())
        assertEquals(2, ReadingHistory.read(reopened).size) // Previous success and the durable orphan fragment.
        crashAt = null
        val latest = generate(target)
        assertEquals(listOf(latest.id), ReadingHistory.read(runs).map { it.id })
        assertEquals(2, runs.history(Int.MAX_VALUE).size)
    }
    @Test fun fragmentSharedWithCurrentParentStaysAvailableForFutureExactCacheHits() {
        val first = generate(target())
        val next = generate(target(), reuse = true)
        assertFalse(first.directory.exists())
        assertEquals(children(first), children(next))
        val third = generate(target(document = "word-memory:third"), reuse = true)
        assertEquals(children(next), children(third)); assertEquals(1, engine.calls)
    }

    @Test fun playbackAndExportLeasesBothHaveToCloseBeforeActualDeletion() {
        val saved = generate(target())
        val playback = runs.acquire(saved.id)
        val export = RunStore(File(root, "results"), File(root, "cache")).acquire(saved.id)
        assertFalse(runs.delete(saved.id)); assertTrue(saved.directory.exists())
        assertTrue(ReadingHistory.read(runs).isEmpty())
        playback.close(); runs.recover(); assertTrue(saved.directory.exists())
        export.close(); runs.recover(); assertFalse(saved.directory.exists())
        children(saved).forEach { assertFalse(File(root, "results/$it").exists()) }
        playback.close(); export.close(); runs.recover()
    }
    @Test fun generationLeaseProtectsSharedFragmentUntilNewParentCommits() {
        val saved = generate(target())
        val id = children(saved).single()
        val lease = runs.acquire(id)
        assertTrue(runs.delete(saved.id)); assertTrue(lease.run.artifact("audio").exists())
        lease.close(); runs.recover(); assertFalse(lease.run.directory.exists())
    }
    @Test fun playerReleasesItsFileOnlyAfterSessionReleaseAndKeepsItDuringPause() {
        val saved = generate(target())
        val lease = runs.acquire(saved.id)
        val order = mutableListOf<String>()
        lateinit var events: PlaybackEvents
        val player = SpeechPlayback(AudioFactory { _, callback ->
            events = callback
            object : AudioSession {
                override fun prepare() {}
                override fun play(speed: Float) {}
                override fun pause() {}
                override fun release() { order += "player" }
            }
        }) { }
        player.play(saved.artifact("audio"), saved.id, Closeable { order += "lease"; lease.close() })
        events.prepared(); player.pause()
        assertFalse(runs.delete(saved.id)); runs.recover(); assertTrue(saved.directory.exists())
        player.resume(); events.completed()
        assertEquals(listOf("player", "lease"), order)
        runs.recover(); assertFalse(saved.directory.exists())
        player.stop(); assertEquals(2, order.size)
    }
    @Test fun traversalAndForgedArtifactNamesCannotEscapePrivateResults() {
        val saved = generate(target())
        val outside = File(root, "keep.txt").also { it.writeText("keep") }
        listOf("../keep.txt", "..", "/absolute", "00000000-0000-0000-0000-00000000000/").forEach { rejected { runs.delete(it) } }
        saved.report.getJSONArray("artifacts").getJSONObject(0).put("name", "../keep.txt")
        rejected { saved.artifact("audio") }
        assertEquals("keep", outside.readText()); assertTrue(saved.directory.exists())
    }
    @Test fun symbolicLinksAreRejectedBeforeAnyResultOrExternalFileIsRemoved() {
        val saved = generate(target())
        val outside = File(root, "keep.txt").also { it.writeText("keep") }
        val link = File(saved.directory, "link.wav")
        try { Files.createSymbolicLink(link.toPath(), outside.toPath()) } catch (failure: Exception) { assumeNoException("Host does not allow test symlinks", failure) }
        try { rejected { runs.delete(saved.id) }; assertEquals("keep", outside.readText()); assertTrue(saved.directory.exists()) }
        finally { Files.deleteIfExists(link.toPath()) }
    }
}
