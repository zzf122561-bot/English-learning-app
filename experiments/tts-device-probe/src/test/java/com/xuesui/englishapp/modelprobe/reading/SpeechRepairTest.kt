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
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exact input/PCM plumbing and failure recovery, with synthetic PCM, never acoustic evidence. */
class SpeechRepairTest {
    private val root = Files.createTempDirectory("speech-repair-").toFile()
    private val descriptor = tinyDescriptor()
    private val models = ModelStore(File(root, "models")) { Long.MAX_VALUE }
    private val runs = RunStore(File(root, "results"), File(root, "cache"))
    private val engine = FakeSpeechEngine(descriptor)
    private val service = SpeechService(engine, models, runs, FakeMetrics()) { }
    private val params = QwenConfiguration(QwenNativeParameters(threads = 1))
    private val queue = ReadingQueue(service, runs)
    init { install(models, descriptor) }
    @After fun cleanup() { engine.unload(); root.deleteRecursively() }
    private fun input(text: String, selection: ReadingSelection = ReadingSelection()) =
        ReadingInput("fixture", "Synthetic", listOf(text), selection)
    private fun target(text: String) = ReadingTarget("fixture:one", ReadingPlanner.create(input(text)))
    private fun audio(p: SpeechParameters, limit: Boolean = false, value: Float = 0.2f) =
        SpeechAudio(FloatArray(240) { if (it % 2 == 0) value else -value }, 24000, p, 3, 20, 5, 28, limit,
            SpeechDiagnostics(12, 1, 1, if (limit) "token_limit" else "eos", 7, 13))

    @Test fun paragraph001RemainsValidWhenUnselectedParagraphIsBlankOrChinese() {
        val selected = ReadingInput("001", "Selected", listOf("A valid English paragraph.", "", "中文备注"),
            ReadingSelection(ReadingScope.PARAGRAPH, 0))
        assertEquals(listOf(selected.paragraphs[0]), ReadingPlanner.create(selected).items.map { it.text })
        rejected { ReadingPlanner.create(selected.copy(selection = ReadingSelection())) }
        rejected { ReadingPlanner.create(selected.copy(selection = ReadingSelection(ReadingScope.PARAGRAPH, 2))) }
        rejected { ReadingPlanner.create(selected.copy(selection = ReadingSelection(ReadingScope.PARAGRAPH, 99))) }
    }

    @Test fun budgetsBoundExactCoverageIncludingNumberedLongParagraphAndUnicode() {
        val text = (1..35).joinToString(" ") { "A${it.toString().padStart(4, '0')}. Lesson $it costs 12.50 dollars in 2026. Speak clearly 😀." }
        for (cap in listOf(64, 256, 1024, 4096)) {
            val config = QwenConfiguration(params.native.copy(maxAudioTokens = cap))
            val plan = ReadingPlanner.create(input(text), config.readingChunkCodePoints)
            assertEquals(text, plan.items.joinToString("") { it.text })
            assertTrue(plan.items.all { it.text.codePointCount(0, it.text.length) <= config.readingChunkCodePoints })
            assertTrue(plan.items.none { Character.isHighSurrogate(it.text.last()) || Character.isLowSurrogate(it.text.first()) })
            plan.items.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        }
        assertEquals(400, params.readingChunkCodePoints)
        assertEquals(96, QwenConfiguration(params.native.copy(maxAudioTokens = 256)).readingChunkCodePoints)
    }

    @Test fun impossibleBudgetPlanRejectsBeforeLoadingRatherThanStartingUnboundedWork() {
        val text = "word ".repeat(19000)
        val small = QwenConfiguration(params.native.copy(maxAudioTokens = 1))
        rejected { queue.generate(ReadingPlanner.create(input(text)), small, WorkToken(), false) { } }
        assertEquals(0, engine.calls); assertEquals(0, engine.loads)
    }

    @Test fun diagnosticAndFormalSinglePieceHaveIdenticalInputParametersAndWave() {
        val text = "A quiet morning gives us time to listen carefully. We read the same short paragraph, keep the original numbers 12 and 2026, and check each sentence in order. Clear words help us understand the story."
        engine.response = { _, p -> audio(p) }
        val diagnostic = service.run(SpeechRequest(text, params, SpeechMode.PARAGRAPH, RunKind.COLD), WorkToken())
        engine.unload()
        val t = target(text)
        val formal = queue.generate(t.plan, params, WorkToken(), false, t) { }
        assertEquals("success", diagnostic.status); assertEquals("success", formal.status)
        assertEquals(listOf(text, text), engine.texts)
        assertEquals(engine.requests[0].cacheParts(), engine.requests[1].cacheParts())
        assertArrayEquals(diagnostic.artifact("audio").readBytes(), formal.artifact("audio").readBytes())
        val timing = formal.report.getJSONObject("timing")
        assertEquals(7L, timing.getLong("prefillMillis")); assertEquals(13L, timing.getLong("tokenGenerationMillis"))
        assertEquals(5L, timing.getLong("decodeMillis"))
        assertTrue(formal.report.getLong("firstCompletedSegmentMillis") >= 0)
        val segment = formal.report.getJSONArray("attempts").getJSONObject(0).getJSONArray("effectiveSegments").getJSONObject(0)
        assertEquals("eos", segment.getJSONObject("native").getString("termination"))
        assertEquals(text.toByteArray().sha256(), segment.getString("textSha256"))
        assertFalse(formal.report.toString().contains(text))
    }

    @Test fun retryReplacesFailedPcmWithTwoExactSlicesAndLoadsModelOnlyOnce() {
        val text = "The first synthetic sentence explains the reading request clearly. The second synthetic sentence checks that every word remains in the final reading."
        engine.response = { _, p -> audio(p, engine.calls == 1, if (engine.calls == 1) 0.9f else 0.2f) }
        val t = target(text)
        val saved = queue.generate(t.plan, params, WorkToken(), false, t) { }
        assertEquals("success", saved.status); assertEquals(3, engine.calls); assertEquals(1, engine.loads)
        assertEquals(text, engine.texts.drop(1).joinToString(""))
        assertEquals(480L, saved.report.getJSONObject("audio").getLong("samples"))
        assertEquals(3, saved.report.getJSONArray("childRunIds").length())
        assertEquals(2, saved.report.getInt("completedCount")); assertEquals(1, saved.report.getInt("repartitions"))
        val firstAttempt = saved.report.getJSONArray("attempts").getJSONObject(0)
        assertEquals("token_limit", firstAttempt.getString("status"))
        val leaves = saved.report.getJSONArray("items")
        val expected = (0 until leaves.length()).flatMap { i ->
            runs.read(leaves.getJSONObject(i).getString("runId")).artifact("audio").readBytes().drop(44) }.toByteArray()
        assertArrayEquals(expected, saved.artifact("audio").readBytes().drop(44).toByteArray())
        assertEquals(saved.id, runs.latest(t.key)!!.id)
        val attemptIds = saved.report.getJSONArray("childRunIds").let { ids -> (0 until ids.length()).map { ids.getString(it) } }
        engine.response = { _, p -> audio(p) }
        val replacement = queue.generate(t.plan, params, WorkToken(), false, t) { }
        assertEquals(replacement.id, runs.latest(t.key)!!.id)
        assertFalse(saved.directory.exists())
        attemptIds.forEach { assertFalse(File(root, "results/$it").exists()) }
    }

    @Test fun exhaustedRetryAndAbnormalOutputPreserveOldSuccessAndNeverBecomeCacheHits() {
        val t = target("A synthetic paragraph contains several careful words and enough content for bounded retries. Another sentence completes the same small fixture.")
        engine.response = { _, p -> audio(p) }
        val good = queue.generate(t.plan, params, WorkToken(), false, t) { }
        val before = engine.calls
        engine.response = { _, p -> audio(p, true) }
        val limited = queue.generate(t.plan, params, WorkToken(), false, t) { }
        assertEquals("token_limit", limited.status)
        assertTrue(engine.calls - before <= ReadingQueue.MAX_RETRY_DEPTH + 1)
        assertEquals(0, limited.report.getInt("completedCount")); assertEquals(good.id, runs.latest(t.key)!!.id)
        val changed = QwenConfiguration(params.native.copy(seed = 123))
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f)) {
            engine.response = { _, p -> audio(p, value = value) }
            val bad = queue.generate(t.plan, changed, WorkToken(), true, t) { }
            assertEquals("failed", bad.status); assertFalse(bad.report.has("audio"))
            assertEquals(good.id, runs.latest(t.key)!!.id)
            val request = SpeechRequest(t.plan.items.single().text, changed, SpeechMode.PARAGRAPH, RunKind.COLD)
            assertNull(runs.cached(SpeechText.key(engine, request, changed)))
        }
    }

    @Test fun cancellationDuringRepartitionNeverLaunchesRetryAndNextParagraphCanGenerate() {
        val text = "The synthetic first sentence is long enough to split into smaller pieces. The second sentence must remain covered in the later request."
        engine.response = { _, p -> audio(p, true) }
        val token = WorkToken()
        val cancelled = queue.generate(ReadingPlanner.create(input(text)), params, token, false) {
            if (it.state == "repartitioning") token.cancel()
        }
        assertEquals("cancelled", cancelled.status); assertEquals(1, engine.calls); assertFalse(engine.loaded)
        engine.response = { _, p -> audio(p) }
        val selected = input("Paragraph zero one is ready.", ReadingSelection(ReadingScope.PARAGRAPH, 0))
        val next = queue.generate(ReadingPlanner.create(selected), params, WorkToken(), false) { }
        assertEquals("success", next.status); assertEquals(2, engine.calls)
    }

    @Test fun manyParagraphsFollowActualBudgetAndRetainEverySourceCharacter() {
        val paragraph = "A clear synthetic sentence provides a reliable fixture. ".repeat(10)
        val input = ReadingInput("many", "118 synthetic paragraphs", List(118) { paragraph }, ReadingSelection())
        engine.response = { _, p -> audio(p) }
        val saved = queue.generate(ReadingPlanner.create(input), params, WorkToken(), false) { }
        assertEquals("success", saved.status); assertEquals(1, engine.loads)
        assertTrue(engine.texts.all { it.length <= 400 })
        assertEquals(input.paragraphs.joinToString(""), engine.texts.joinToString(""))
        assertEquals(engine.calls, saved.report.getInt("completedCount"))
    }

    @Test fun cleanNarrationCannotReuseNumberedInputButOrdinaryNumbersReachEngine() {
        val old = target("A0001. There are 12 birds in 2026.")
        val newer = target("There are 12 birds in 2026.")
        engine.response = { _, p -> audio(p) }
        val first = queue.generate(old.plan, params, WorkToken(), true, old) { }
        assertFalse(newer.matches(first, params, descriptor.fingerprint, descriptor.engineVersion))
        val second = queue.generate(newer.plan, params, WorkToken(), true, newer) { }
        assertEquals(2, engine.calls); assertEquals("There are 12 birds in 2026.", engine.texts.last())
        assertFalse(second.report.getJSONArray("items").getJSONObject(0).getBoolean("reused"))
    }

    @Test fun effectiveMismatchOrNonEosCannotPublishAsSuccessfulAudio() {
        val t = target("A short synthetic sentence.")
        engine.response = { _, p -> audio(QwenConfiguration((p as QwenConfiguration).native.copy(seed = 99))) }
        assertEquals("failed", queue.generate(t.plan, params, WorkToken(), false, t) { }.status)
        assertNull(runs.latest(t.key))
        engine.response = { _, p -> audio(p).copy(diagnostics = SpeechDiagnostics(12, 1, 1, "unknown", 7, 13)) }
        assertEquals("failed", queue.generate(t.plan, params, WorkToken(), false, t) { }.status)
        assertNull(runs.latest(t.key))
    }

    @Test fun compatibleSettingsUpgradeKeepsVoiceBudgetAndOldFile() {
        val directory = File(root, "settings")
        val store = SettingsStore(directory)
        val custom = QwenConfiguration(params.native.copy(speaker = "aiden", maxAudioTokens = 512, instruction = "Calm", seed = 123))
        val snapshot = JSONObject().put("generation", custom.snapshot()).put("speech", JSONObject())
        store.save("model", QwenModel.PREVIOUS_ENGINE_VERSION, 2, snapshot)
        val before = directory.listFiles()!!.associate { it.name to it.readBytes().toList() }
        val value = store.load("model", QwenModel.descriptor.engineVersion, 2, setOf(QwenModel.PREVIOUS_ENGINE_VERSION))!!
        assertEquals(custom, QwenConfiguration.fromJson(value.getJSONObject("generation")))
        assertEquals(before, directory.listFiles()!!.associate { it.name to it.readBytes().toList() })
        rejected { store.load("model", "unapproved-engine", 2) }
    }

    @Test fun globalRetryBudgetStopsManyProblemParagraphsWithoutStartingTheRest() {
        val text = "The first synthetic sentence has enough words for retry. The second synthetic sentence is also clear."
        val longInput = ReadingInput("many-limits", "Synthetic", List(40) { text }, ReadingSelection())
        engine.response = { part, p -> audio(p, part == text) }
        val saved = queue.generate(ReadingPlanner.create(longInput), params, WorkToken(), false) { }
        assertEquals("token_limit", saved.status)
        assertEquals(ReadingQueue.MAX_REPARTITIONS, saved.report.getInt("repartitions"))
        assertEquals(ReadingQueue.MAX_REPARTITIONS * 3 + 1, engine.calls)
        assertEquals(ReadingQueue.MAX_REPARTITIONS * 2, saved.report.getInt("completedCount"))
        assertEquals("not_started", saved.report.getJSONArray("items").getJSONObject(saved.report.getInt("plannedCount") - 1).getString("status"))
    }
}
