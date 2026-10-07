package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.SpeechService
import com.xuesui.englishapp.modelprobe.stage2.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DuplicateSentenceTest {
    private val root = Files.createTempDirectory("duplicate-sentence-").toFile()
    private val descriptor = tinyDescriptor()
    private val models = ModelStore(File(root, "models")) { Long.MAX_VALUE }
    private val runs = RunStore(File(root, "results"), File(root, "cache"))
    private val engine = FakeSpeechEngine(descriptor)
    private val spoken = mutableListOf<String>()
    private val recordedEngine = object : SpeechEngine by engine {
        override fun synthesize(text: String, parameters: SpeechParameters, token: WorkToken): SpeechAudio {
            spoken += text
            return engine.synthesize(text, parameters, token)
        }
    }
    private val queue = ReadingQueue(SpeechService(recordedEngine, models, runs, FakeMetrics()) { }, runs)
    private val params = QwenConfiguration(QwenNativeParameters(threads = 1))
    init { install(models, descriptor) }
    @After fun cleanup() { engine.unload(); root.deleteRecursively() }
    private fun target(sentence: String = "Echo again.", text: List<String> = listOf("Echo again. Other words. Echo again."),
        document: String = "word-memory:one", paragraph: Int = 0, title: String = "Fixture") = ReadingTarget(document,
        ReadingPlanner.create(ReadingInput(UUID.randomUUID().toString(), title, text, ReadingSelection(ReadingScope.SENTENCE, paragraph, sentence))))
    private fun generate(target: ReadingTarget, parameters: QwenConfiguration = params, reuse: Boolean = false) =
        queue.generate(target.plan, parameters, WorkToken(), reuse, target) { }
    private fun assertUnspecified(position: JSONObject, count: Int) {
        assertEquals("unspecified", position.getString("occurrence"))
        assertEquals(count, position.getInt("matchingOccurrences"))
        listOf("firstSentence", "lastSentence", "startOffset", "endOffset").forEach { assertTrue("$it must not pretend to identify an occurrence", position.isNull(it)) }
    }

    @Test fun uniqueSentenceKeepsItsPreviousPositionKeyLabelAndSourceOffsets() {
        val target = target(sentence = "Second sentence.", text = listOf("First sentence. Second sentence."))
        val oldKey = hashParts(listOf("reading-retention-v1", "word-memory:one", "SENTENCE", "0", "1:1"))
        assertEquals(oldKey, target.key)
        assertEquals("第1段 · 第2句", target.label)
        assertEquals("1:1", target.snapshot().getString("sentenceRange"))
        val saved = generate(target)
        val row = saved.report.getJSONArray("items").getJSONObject(0)
        assertEquals(1, row.getInt("firstSentence")); assertEquals(1, row.getInt("lastSentence"))
        assertEquals("First sentence. ".length, row.getInt("startOffset"))
        assertEquals("First sentence. Second sentence.".length, row.getInt("endOffset"))
        assertEquals(listOf("Second sentence."), spoken)
    }
    @Test fun repeatedContentIdentityIsStableAcrossOccurrencePositionCountTitleAndRequestId() {
        val initial = target()
        val changed = target(text = listOf("Preface. Echo again. Echo again. Final words. Echo again."), title = "Renamed")
        assertEquals(initial.key, changed.key)
        assertEquals(initial.bodyKey, changed.bodyKey)
        assertNotEquals(initial.plan.input.id, changed.plan.input.id)
        assertEquals("第1段 · 单句（重复文本）", changed.label)
        val snapshot = changed.snapshot()
        assertEquals("unspecified", snapshot.getString("occurrence")); assertEquals(3, snapshot.getInt("matchingOccurrences"))
        assertTrue(snapshot.isNull("sentenceRange")); assertTrue(snapshot.getString("sentenceIdentity").startsWith("repeated:"))
        assertEquals(initial.key, target(sentence = " Echo again. ").key) // Existing trim semantics.
        assertNotEquals(initial.key, target(document = "word-memory:other").key)
        assertNotEquals(initial.key, target(text = listOf("Echo again. Echo again.", "Echo again. Echo again."), paragraph = 1).key)
        assertNotEquals(initial.key, target(sentence = "Other words.", text = listOf("Other words. Other words.")).key)
        assertNotEquals(initial.key, target(sentence = "echo again.", text = listOf("echo again. echo again.")).key)
    }
    @Test fun duplicateSelectionIsSynthesizedOnceWithUnspecifiedParentAndChildPositions() {
        val target = target()
        assertEquals(listOf("Echo again."), target.plan.items.map { it.text })
        assertTrue(target.plan.items.single().label.contains("单句（重复文本）"))
        val saved = generate(target)
        assertEquals(1, engine.calls); assertEquals(listOf("Echo again."), spoken)
        assertEquals(240, saved.report.getJSONObject("audio").getInt("samples"))
        val row = saved.report.getJSONArray("items").getJSONObject(0)
        assertUnspecified(row, 2)
        assertFalse(row.getString("label").contains("第1句"))
        assertUnspecified(runs.read(row.getString("runId")).report.getJSONObject("reading"), 2)
        assertEquals("unspecified", saved.report.getJSONObject("retention").getString("occurrence"))
        assertFalse(saved.report.toString().contains("Echo again."))
    }
    @Test fun longRepeatedSentenceIsChunkedOnceWithoutClaimingFirstOccurrenceInAnyRow() {
        val sentence = "Long " + "word ".repeat(210) + "ending."
        val target = target(sentence, listOf("$sentence Other words. $sentence"))
        assertTrue(target.plan.items.size > 1)
        assertEquals(sentence, target.plan.items.joinToString("") { it.text })
        val saved = generate(target)
        assertEquals(saved.report.getInt("plannedCount"), engine.calls); assertEquals(sentence, spoken.joinToString(""))
        val rows = saved.report.getJSONArray("items")
        repeat(rows.length()) { i ->
            val row = rows.getJSONObject(i)
            assertUnspecified(row, 2)
            assertTrue(row.getString("label").contains("单句（重复文本）"))
            assertUnspecified(runs.read(row.getString("runId")).report.getJSONObject("reading"), 2)
        }
    }
    @Test fun repeatedRangeReplacesAcrossVoiceAndSeedWhileOtherRangesAndOldUniqueHistorySurvive() {
        val unique = generate(target(text = listOf("Echo again.")))
        val repeated = target(); val previous = generate(repeated)
        val otherDocument = generate(target(document = "word-memory:two"))
        val otherParagraph = generate(target(text = listOf("Unused.", "Echo again. Echo again."), paragraph = 1))
        val changed = params.copy(native = params.native.copy(speaker = "aiden", instruction = "Clear and calm", seed = 91))
        val latest = generate(target(title = "Renamed"), changed)
        assertEquals(latest.id, runs.latest(repeated.key)!!.id); assertFalse(previous.directory.exists())
        assertTrue(unique.directory.exists()); assertTrue(otherDocument.directory.exists()); assertTrue(otherParagraph.directory.exists())
        assertEquals(4, ReadingHistory.read(runs).size)
        assertEquals("aiden", latest.report.getJSONObject("requested").getString("speaker"))
        assertEquals(91L, latest.report.getJSONObject("requested").getLong("seed"))
    }
    @Test fun cachedRepeatKeepsCurrentAmbiguityMetadataAndInvalidSelectionsStillReject() {
        val first = generate(target())
        val second = generate(target(text = listOf("Echo again. Echo again. Echo again.")), reuse = true)
        assertEquals(1, engine.calls)
        val row = second.report.getJSONArray("items").getJSONObject(0)
        assertEquals("cache", row.getString("status")); assertUnspecified(row, 3)
        assertEquals(first.report.getJSONArray("items").getJSONObject(0).getString("runId"), row.getString("runId"))
        // Reused source reports retain their original provenance; current selection is on the parent.
        assertUnspecified(runs.read(row.getString("runId")).report.getJSONObject("reading"), 2)
        rejected { target(sentence = "") }
        rejected { target(sentence = "Not in this paragraph.") }
        rejected { target(paragraph = 5) }
    }
}
