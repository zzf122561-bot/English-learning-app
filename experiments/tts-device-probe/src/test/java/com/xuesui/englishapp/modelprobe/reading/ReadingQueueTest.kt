package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import com.xuesui.englishapp.modelprobe.stage2.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ReadingQueueTest {
    private val root = Files.createTempDirectory("reading-queue-").toFile()
    private val models = ModelStore(File(root, "models")) { Long.MAX_VALUE }
    private val descriptor = tinyDescriptor()
    private val engine = FakeSpeechEngine(descriptor)
    private val runs = RunStore(File(root, "results"), File(root, "cache"))
    private val service = SpeechService(engine, models, runs, FakeMetrics()) { }
    private val queue = ReadingQueue(service, runs)
    private val params = QwenConfiguration(QwenNativeParameters(threads = 1))
    private val input = ReadingInput("synthetic-request", "Reading fixture", listOf("First paragraph.", "Second paragraph.", "Third paragraph."), ReadingSelection())
    private fun plan() = ReadingPlanner.create(input)
    init { install(models, descriptor) }
    @After fun cleanup() { engine.unload(); root.deleteRecursively() }
    @Test fun completeQueueSavesEachPartAndCombinedWaveWithCorrectOriginAndOrder() {
        val saved = queue.generate(plan(), params, WorkToken(), true) { }
        assertEquals("success", saved.status); assertEquals(3, engine.calls); assertEquals(1, engine.loads)
        assertEquals(listOf(42L, 43L, 44L), engine.requests.map { (it as QwenConfiguration).native.seed })
        val report = saved.report; assertEquals(3, report.getInt("completedCount"))
        val bytes = saved.artifact("audio").readBytes()
        assertEquals(44 + 3 * 480, bytes.size)
        assertEquals(1440, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(40))
        assertEquals(720, report.getJSONObject("audio").getLong("samples"))
        repeat(3) { i ->
            val row = report.getJSONArray("items").getJSONObject(i)
            val child = runs.read(row.getString("runId"))
            assertEquals(i, child.report.getJSONObject("reading").getInt("paragraphIndex"))
            assertEquals(saved.id, child.report.getJSONObject("reading").getString("sessionId"))
            assertEquals(input.paragraphs[i].toByteArray().sha256(), child.report.getString("textSha256"))
        }
        assertFalse(report.toString().contains("First paragraph."))
        assertEquals("pending", report.getJSONObject("validation").getString("compatibilityGate"))
    }
    @Test fun cancellationBetweenPartsPreservesCompletedAudioAndNeverStartsNextPart() {
        val token = WorkToken()
        val saved = queue.generate(plan(), params, token, false) { if (it.state == "success") token.cancel() }
        assertEquals("cancelled", saved.status); assertEquals(1, engine.calls)
        assertEquals(1, saved.report.getInt("completedCount")); assertTrue(saved.artifact("audio").isFile)
        assertEquals("not_started", saved.report.getJSONArray("items").getJSONObject(1).getString("status"))
        assertFalse(engine.loaded)
        assertEquals("success", queue.generate(plan(), params, WorkToken(), false) { }.status)
    }
    @Test fun nativeFailureAndTokenLimitStopQueueWithoutClaimingFullCompletion() {
        engine.action = { if (engine.calls == 2) error("Synthetic native failure") }
        val failed = queue.generate(plan(), params, WorkToken(), false) { }
        assertEquals("failed", failed.status); assertEquals(2, engine.calls)
        assertEquals(1, failed.report.getInt("completedCount"))
        assertEquals("not_started", failed.report.getJSONArray("items").getJSONObject(2).getString("status"))
        engine.action = null; engine.limit = true
        val limited = queue.generate(plan(), params, WorkToken(), false) { }
        assertEquals("token_limit", limited.status); assertEquals(3, engine.calls)
        assertEquals(0, limited.report.getInt("completedCount")); assertTrue(limited.report.has("audio"))
    }
    @Test fun savedCacheCanBeReusedWithNewHostIdentityWithoutLoadingMissingModel() {
        queue.generate(plan(), params, WorkToken(), true) { }; engine.unload(); models.delete(descriptor)
        val again = queue.generate(ReadingPlanner.create(input.copy(id = "new-request")), params, WorkToken(), true) { }
        assertEquals("success", again.status); assertEquals(3, engine.calls); assertEquals(1, engine.loads)
        val child = runs.read(again.report.getJSONArray("items").getJSONObject(0).getString("runId"))
        assertEquals("cache", child.status); assertTrue(child.report.isNull("rtf"))
        assertEquals("new-request", child.report.getJSONObject("reading").getString("requestId"))
    }
    @Test fun speakerAndNaturalLanguageInstructionReachEngineAndInvalidateOldCache() {
        queue.generate(plan(), params, WorkToken(), true) { }
        val changed = QwenConfiguration(params.native.copy(speaker = "aiden", instruction = "温和、清晰，适合听写。"))
        val saved = queue.generate(plan(), changed, WorkToken(), true) { }
        assertEquals(6, engine.calls)
        assertTrue(engine.requests.takeLast(3).all { (it as QwenConfiguration).native.instruction == changed.native.instruction && it.native.speaker == "aiden" })
        assertEquals(changed.native.instruction, saved.report.getJSONObject("requested").getString("instruction"))
        val child = runs.read(saved.report.getJSONArray("items").getJSONObject(0).getString("runId"))
        assertEquals(input.paragraphs[0].toByteArray().sha256(), child.report.getString("textSha256"))
        assertEquals(changed.native.instruction, child.report.getJSONArray("effectiveSegments").getJSONObject(0).getJSONObject("parameters").getString("instruction"))
    }
    @Test fun randomQueueRecordsOriginalRequestAndOneExplicitBaseWithDerivedSeeds() {
        val saved = queue.generate(plan(), QwenConfiguration(params.native.copy(seed = -1)), WorkToken(), true) { }
        val seed = saved.report.getJSONObject("resolvedBaseParameters").getLong("seed")
        assertEquals(-1, saved.report.getJSONObject("requested").getLong("seed"))
        assertEquals((0..2).map { (seed + it) % (Int.MAX_VALUE.toLong() + 1) }, engine.requests.map { (it as QwenConfiguration).native.seed })
    }
    @Test fun corruptedWaveJoinKeepsChildRecordsButCannotReportSuccessfulWholeAudio() {
        val saved = queue.generate(plan(), params, WorkToken(), false) { progress ->
            if (progress.item.index == 0 && progress.run != null) progress.run.artifact("audio").writeBytes(byteArrayOf(1, 2, 3))
        }
        assertEquals("failed", saved.status); assertFalse(saved.report.has("audio"))
        assertTrue(saved.report.getString("error").contains("合并WAV失败"))
        assertEquals(3, saved.report.getJSONArray("items").length())
    }
    @Test fun historyLimitsWholeReadingsAfterGroupingAndKeepsChildrenFromInterruptedProcess() {
        fun save(task: String, time: Long, parent: String? = null) = runs.begin().use { draft ->
            val json = JSONObject().put("taskId", task).put("status", "success").put("createdAtEpochMillis", time)
            if (parent != null) json.put("reading", JSONObject().put("sessionId", parent))
            draft.commit(json, emptyMap())
        }
        val old = save("speech.reading.v1", 1)
        val group = save("speech.reading.v1", 2)
        repeat(105) { save("speech.synthesis.v1", 10L + it, group.id) }
        val orphan = save("speech.synthesis.v1", 200, java.util.UUID.randomUUID().toString())
        val visible = ReadingHistory.read(runs)
        assertEquals(listOf(orphan.id, group.id, old.id), visible.map { it.id })
        assertEquals(100, runs.history().size)
    }
}
