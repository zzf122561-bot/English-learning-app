package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.reading.*
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** State/persistence-contract tests; Android SQLite is separately covered by instrumentation. */
class ParagraphLedgerTest {
    private class Disk : QueuePersistence {
        var bytes: String? = null
        var fail = false
        override fun load(): LedgerState {
            val j = bytes?.let(::JSONObject) ?: return LedgerState()
            return LedgerState(j.getJSONArray("batches").objects().map(SpeechBatch::read), j.getJSONArray("jobs").objects().map(ParagraphJob::read),
                j.getJSONArray("audio").objects().map(ParagraphAudio::read).associateBy { it.key },
                j.getJSONArray("garbage").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
        }
        override fun save(state: LedgerState) {
            if (fail) throw java.io.IOException("synthetic full disk")
            bytes = JSONObject().put("batches", JSONArray(state.batches.map { it.json() })).put("jobs", JSONArray(state.jobs.map { it.json() }))
                .put("audio", JSONArray(state.latest.values.map { it.json() })).put("garbage", JSONArray(state.garbage.toList())).toString()
        }
    }
    private val config = QwenConfiguration(QwenNativeParameters(threads = 1))
    private fun target(doc: String = "doc:one", count: Int = 3, text: String = "A clear sentence.") = ReadingTarget(doc,
        ReadingPlanner.create(ReadingInput(UUID.randomUUID().toString(), "Fixture", List(count) { "$text Paragraph ${it + 1}." }, ReadingSelection(ReadingScope.FULL))))
    private fun complete(ledger: ParagraphLedger): String {
        val job = ledger.claim()!!; val run = UUID.randomUUID().toString()
        ledger.recordDraft(job.id, run); assertTrue(ledger.publish(job.id, run)); return run
    }
    @Test fun fortyFourParagraphRestartKeepsFortyThreeAndFrozenSeeds() {
        val disk = Disk(); val first = ParagraphLedger(disk)
        val id = first.enqueue(target(count = 44), config.copy(native = config.native.copy(seed = -1)), "engine")
        val frozen = first.state.jobs.associate { it.id to it.parameters }
        repeat(43) { complete(first) }
        val interrupted = first.claim()!!; first.recordDraft(interrupted.id, UUID.randomUUID().toString())
        val restarted = ParagraphLedger(disk)
        assertEquals(43, restarted.state.completed(id))
        restarted.interruptRunning("synthetic process death")
        val next = restarted.claim()!!
        assertEquals(43, next.index); assertEquals(frozen[next.id], next.parameters)
        assertEquals(44, restarted.state.jobs.size)
        assertEquals(43, restarted.state.latest.size)
        assertTrue(restarted.state.jobs.all { QwenConfiguration.fromJson(JSONObject(it.parameters)).native.seed >= 0 })
    }
    @Test fun userPauseAndCancelNeverResumeOnProcessRecovery() {
        val disk = Disk(); val ledger = ParagraphLedger(disk)
        val paused = ledger.enqueue(target(), config, "engine"); complete(ledger)
        ledger.control(paused, BatchPhase.PAUSED, "user")
        val cancelled = ledger.enqueue(target("doc:two"), config, "engine")
        ledger.control(cancelled, BatchPhase.CANCELLED, "user")
        val fresh = ParagraphLedger(disk); fresh.interruptRunning("process")
        assertNull(fresh.claim()); assertEquals(1, fresh.state.completed(paused))
        fresh.control(paused, BatchPhase.QUEUED); assertEquals(1, fresh.claim()!!.index)
        assertThrows(IllegalArgumentException::class.java) { fresh.control(cancelled, BatchPhase.QUEUED) }
    }
    @Test fun failedParagraphPausesOwnRemainderButNextDocumentRuns() {
        val ledger = ParagraphLedger(Disk())
        val first = ledger.enqueue(target(), config, "engine")
        val second = ledger.enqueue(target("doc:two"), config, "engine")
        complete(ledger)
        val failed = ledger.claim()!!; ledger.fail(failed.id, "token_limit")
        assertEquals(BatchPhase.FAILED, ledger.state.batches.first().phase)
        assertEquals(second, ledger.claim()!!.batch)
        assertEquals(1, ledger.state.completed(first))
    }
    @Test fun duplicateClicksAcrossThreadsPersistOneWholeBatch() {
        val ledger = ParagraphLedger(Disk()); val request = target(count = 44)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val ids = (0..19).map { pool.submit<String> { ledger.enqueue(request, config, "engine") } }.map { it.get() }.toSet()
            assertEquals(1, ids.size); assertEquals(44, ledger.state.jobs.size); assertEquals(1, ledger.state.batches.size)
        } finally { pool.shutdownNow() }
    }
    @Test fun cannotClaimSecondNativeOwnerBeforeFirstReturns() {
        val ledger = ParagraphLedger(Disk()); ledger.enqueue(target(), config, "engine"); ledger.claim()
        assertThrows(IllegalStateException::class.java) { ledger.claim() }
    }
    @Test fun transactionFailureDoesNotExposeHalfBatchOrNewAudio() {
        val disk = Disk(); val ledger = ParagraphLedger(disk); disk.fail = true
        assertThrows(java.io.IOException::class.java) { ledger.enqueue(target(), config, "engine") }
        assertTrue(ledger.state.jobs.isEmpty()); assertTrue(ParagraphLedger(disk).state.jobs.isEmpty())
        disk.fail = false; ledger.enqueue(target(count = 1), config, "engine"); val original = complete(ledger)
        ledger.enqueue(target(count = 1), config.copy(native = config.native.copy(seed = 77)), "engine")
        val job = ledger.claim()!!; val run = UUID.randomUUID().toString(); ledger.recordDraft(job.id, run); disk.fail = true
        assertThrows(java.io.IOException::class.java) { ledger.publish(job.id, run) }
        assertEquals(original, ledger.state.latest.values.single().run)
        assertEquals(original, ParagraphLedger(disk).state.latest.values.single().run)
    }
    @Test fun explicitDeletionFencesLatePublicationAndDoesNotResurrect() {
        val disk = Disk(); val ledger = ParagraphLedger(disk)
        ledger.enqueue(target(count = 1), config, "engine"); val old = complete(ledger)
        ledger.enqueue(target(count = 1), config.copy(native = config.native.copy(seed = 51)), "engine")
        val job = ledger.claim()!!
        ledger.deleteAudio(old)
        assertFalse(ledger.publish(job.id, UUID.randomUUID().toString()))
        val restarted = ParagraphLedger(disk); restarted.interruptRunning("restart")
        assertTrue(restarted.state.latest.isEmpty()); assertNull(restarted.claim())
    }
    @Test fun parameterAndBodyChangeCreateNewSnapshotWhileFailurePreservesOld() {
        val ledger = ParagraphLedger(Disk())
        val before = target(count = 1); ledger.enqueue(before, config, "engine"); val old = complete(ledger)
        val newer = target(count = 1, text = "Different content.")
        val changed = config.copy(native = config.native.copy(speaker = "aiden", seed = 88))
        val id = ledger.enqueue(newer, changed, "engine")
        val frozen = ledger.state.jobs(id).single().parameters
        val job = ledger.claim()!!; ledger.fail(job.id, "out of space")
        assertEquals(old, ledger.state.latest.values.single().run)
        assertEquals("aiden", JSONObject(frozen).getString("speaker")); assertEquals(88, JSONObject(frozen).getLong("seed"))
        assertNotEquals(ledger.state.batches.first().snapshotKey, ledger.state.batches.last().snapshotKey)
    }
    @Test fun pausedBatchPinsCompletedParagraphEvenAfterNewLatestReplacesIt() {
        val ledger = ParagraphLedger(Disk()); val first = ledger.enqueue(target(), config, "engine"); val old = complete(ledger)
        ledger.control(first, BatchPhase.PAUSED)
        val single = target().let { it.copy(plan = ReadingPlanner.create(it.plan.input.copy(selection = ReadingSelection(ReadingScope.PARAGRAPH, 0)))) }
        ledger.enqueue(single, config.copy(native = config.native.copy(seed = 79)), "engine"); val latest = complete(ledger)
        assertTrue(old in ledger.state.pins()); assertTrue(latest in ledger.state.pins()); assertTrue(old in ledger.state.garbage)
        ledger.control(first, BatchPhase.CANCELLED)
        assertFalse(old in ledger.state.pins()); assertTrue(latest in ledger.state.pins())
    }
    @Test fun fillMissingPreservesExistingRandomTakeAndOnlyClaimsTheGap() {
        val ledger = ParagraphLedger(Disk())
        val oldSeed = config.copy(native = config.native.copy(seed = 345)).snapshot().toString()
        val id = ledger.enqueue(target(), config.copy(native = config.native.copy(seed = -1)), "engine",
            mapOf(0 to ReadyParagraph(UUID.randomUUID().toString(), oldSeed), 2 to ReadyParagraph(UUID.randomUUID().toString(), oldSeed)))
        assertEquals(2, ledger.state.completed(id)); assertEquals(oldSeed, ledger.state.jobs(id).first().parameters)
        assertEquals(1, ledger.claim()!!.index)
    }
}
