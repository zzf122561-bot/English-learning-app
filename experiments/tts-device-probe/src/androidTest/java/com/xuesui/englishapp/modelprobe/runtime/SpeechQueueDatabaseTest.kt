package com.xuesui.englishapp.modelprobe.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.reading.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Android SQLiteOpenHelper against private synthetic fixtures, never app data.
 * Compilation alone is not execution of these tests or background-service device acceptance. */
@RunWith(AndroidJUnit4::class)
class SpeechQueueDatabaseTest {
    @Test fun sqliteReopenPreservesFortyThreeOfFortyFourAndUserPause() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "queue-db-fixture-${UUID.randomUUID()}").also { check(it.mkdir()) }
        val dbFile = File(directory, "queue.sqlite3")
        val config = QwenConfiguration(QwenNativeParameters(threads = 1, seed = -1))
        val target = ReadingTarget("synthetic:44", ReadingPlanner.create(ReadingInput("fixture", "Synthetic",
            List(44) { "This is synthetic paragraph ${it + 1}." }, ReadingSelection(ReadingScope.FULL))))
        var db = SqliteSpeechQueue(context, dbFile)
        try {
            val first = ParagraphLedger(db); val id = first.enqueue(target, config, "synthetic-engine")
            val frozen = first.state.jobs.last().parameters
            repeat(43) { val job = first.claim()!!; val run = UUID.randomUUID().toString(); first.recordDraft(job.id, run); first.publish(job.id, run) }
            first.claim(); db.close()
            db = SqliteSpeechQueue(context, dbFile)
            val restored = ParagraphLedger(db); restored.interruptRunning("synthetic process death")
            assertEquals(43, restored.state.completed(id)); assertEquals(44, restored.state.jobs.size)
            assertEquals(frozen, restored.claim()!!.parameters)
            restored.control(id, BatchPhase.PAUSED, "user"); db.close()
            db = SqliteSpeechQueue(context, dbFile)
            val paused = ParagraphLedger(db); paused.interruptRunning("reopen")
            assertNull(paused.claim()); assertEquals(43, paused.state.latest.size)
            assertEquals(1, db.readableDatabase.version)
        } finally { db.close(); directory.deleteRecursively() }
    }
    @Test fun sqliteWriteFailureRollsBackWholeSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "queue-transaction-fixture-${UUID.randomUUID()}").also { check(it.mkdir()) }
        val db = SqliteSpeechQueue(context, File(directory, "queue.sqlite3"))
        try {
            val ledger = ParagraphLedger(db)
            val target = ReadingTarget("synthetic:rollback", ReadingPlanner.create(ReadingInput("r", "Fixture", listOf("Clear English."), ReadingSelection(ReadingScope.FULL))))
            ledger.enqueue(target, QwenConfiguration(QwenNativeParameters(threads = 1)), "engine")
            val original = db.load()
            val broken = original.copy(jobs = original.jobs + original.jobs.single().copy(id = UUID.randomUUID().toString()))
            var rejected = false
            try { db.save(broken) } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
            assertTrue(rejected); assertEquals(original, db.load())
        } finally { db.close(); directory.deleteRecursively() }
    }
}
