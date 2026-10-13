package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.core.storage.RunStore
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.ParameterHelp
import com.xuesui.englishapp.modelprobe.features.reading.PlaybackIntent
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DurableAudioTest {
    private val root = Files.createTempDirectory("durable-audio-").toFile()
    private fun store() = RunStore(File(root, "runs"), File(root, "cache"))
    private fun audio(runs: RunStore, session: String? = null, children: List<String> = emptyList()) = runs.begin().use { draft ->
        draft.file("audio.wav").writeBytes(byteArrayOf(1, 2, 3, 4))
        draft.commit(JSONObject().put("taskId", "speech.reading.v1").put("status", "success").put("audio", JSONObject())
            .put("childRunIds", JSONArray(children)).also { if (session != null) it.put("reading", JSONObject().put("sessionId", session)) }, mapOf("audio" to "audio.wav"))
    }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun durableReferenceProtectsFilesThroughNewStoreAndGarbageCollection() {
        val runs = store(); val saved = audio(runs)
        runs.durablePins("queue", setOf(saved.id)); assertFalse(runs.delete(saved.id))
        val restarted = store(); assertTrue(saved.directory.exists()); restarted.acquire(saved.id).close()
        restarted.durablePins("queue", emptySet()); restarted.recover(); assertFalse(saved.directory.exists())
    }
    @Test fun queueAndPlayerPinsRemainIndependentAndProtectChildren() {
        val runs = store(); val child = audio(runs, UUID.randomUUID().toString()); val parent = audio(runs, children = listOf(child.id))
        runs.durablePins("queue", setOf(parent.id)); runs.durablePins("player", setOf(parent.id))
        runs.delete(parent.id); runs.durablePins("queue", emptySet()); store().recover()
        assertTrue(parent.directory.exists()); assertTrue(child.directory.exists())
        runs.durablePins("player", emptySet()); runs.recover(); assertFalse(parent.directory.exists()); assertFalse(child.directory.exists())
    }
    @Test fun crashBeforeParagraphParentCommitRetiresOnlyItsUnreferencedChildren() {
        val runs = store(); val abandoned = UUID.randomUUID().toString()
        val child = audio(runs, abandoned); val unrelated = audio(runs)
        assertTrue(runs.delete(abandoned)); assertFalse(child.directory.exists()); assertTrue(unrelated.directory.exists())
    }
    @Test fun publicationUnionSurvivesDatabaseFailureUntilReconciled() {
        val runs = store(); val old = audio(runs); val proposed = audio(runs)
        runs.durablePins("queue", setOf(old.id)); runs.durablePins("queue", setOf(proposed.id), union = true)
        runs.delete(old.id); runs.delete(proposed.id)
        val restart = store(); assertTrue(old.directory.exists()); assertTrue(proposed.directory.exists())
        restart.durablePins("queue", setOf(old.id)); restart.recover()
        assertTrue(old.directory.exists()); assertFalse(proposed.directory.exists())
    }
    @Test fun fillWaitsAtGapWhileExplicitExistingOnlyUsesActualIndexes() {
        val fill = PlaybackIntent("full", listOf(0, 1, 2))
        assertTrue(fill.ready(setOf(0, 2))); fill.ended()
        assertFalse(fill.ready(setOf(0, 2))); assertEquals(1, fill.paragraph)
        assertTrue(fill.ready(setOf(0, 1, 2)))
        val existing = PlaybackIntent("existing", listOf(0, 2)); existing.ended(); assertEquals(2, existing.paragraph)
    }
    @Test fun pauseAndTargetSwitchCannotBeUndoneByLateAvailability() {
        val old = PlaybackIntent("old", listOf(1)); old.pause()
        assertFalse(old.ready(setOf(1)))
        val selected = PlaybackIntent("new", listOf(3)); assertFalse(selected.ready(setOf(1)))
        selected.pause(); assertFalse(selected.ready(setOf(3))); selected.resume(); assertTrue(selected.ready(setOf(3)))
        selected.ended(); assertFalse(selected.desired); assertNull(selected.paragraph)
    }
    @Test fun chineseHelpExplainsActualTradeoffsAndFrameBudgetBoundary() {
        assertTrue(ParameterHelp.threads.contains("不一定更快")); assertTrue(ParameterHelp.threads.contains("发热"))
        assertTrue(ParameterHelp.frames.contains("不是全文长度限制")); assertTrue(ParameterHelp.repetition.contains("完整性"))
        assertTrue(ParameterHelp.seed.contains("恢复任务不重新抽取")); assertTrue(ParameterHelp.topP.contains("累计概率"))
    }
}
