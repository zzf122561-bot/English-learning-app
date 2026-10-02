package com.xuesui.englishapp.modelprobe.stage2

import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.speech.SpeechPreferences
import com.xuesui.englishapp.modelprobe.contracts.speech.SpeechMode
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class StorageSettingsTest {
    private val root = Files.createTempDirectory("probe-storage-test-").toFile()
    private val store = RunStore(File(root, "results"), File(root, "cache"))
    @After fun clean() { root.deleteRecursively() }
    private fun save(key: String): SavedRun = store.begin().use { draft ->
        draft.file("audio.wav").writeBytes(byteArrayOf(1, 2, 3))
        draft.commit(JSONObject().put("status", "success").put("createdAtEpochMillis", 1), mapOf("audio" to "audio.wav"), key)
    }
    @Test fun onlyAtomicallyCommittedAudioReportPairsBecomeHistory() {
        store.begin().use { it.file("audio.wav").writeText("unfinished") }
        assertTrue(store.history().isEmpty())
        val run = save(hashParts(listOf("key")))
        assertEquals(run.id, store.history().single().id)
        assertEquals(3, store.read(run.id).artifact("audio").length())
    }
    @Test fun tamperedArtifactsCannotBePlayedExportedOrReusedAsCache() {
        val key = hashParts(listOf("key")); val run = save(key)
        run.artifact("audio").writeBytes(byteArrayOf(4, 5, 6))
        rejected { store.read(run.id) }; assertNull(store.cached(key))
    }
    @Test fun clearingCachePreservesHistoryAndModelStorageIsIndependent() {
        val key = hashParts(listOf("key")); val run = save(key)
        assertNotNull(store.cached(key)); assertNull(store.cached(hashParts(listOf("other"))))
        store.clearCache(); assertNull(store.cached(key)); assertTrue(store.read(run.id).artifact("audio").isFile)
        val models = com.xuesui.englishapp.modelprobe.core.model.ModelStore(File(root, "models")) { Long.MAX_VALUE }
        val d = tinyDescriptor(); install(models, d); models.delete(d)
        assertTrue(store.read(run.id).artifact("audio").isFile)
    }
    @Test fun allEngineParametersRoundTripAndDifferentEngineVersionDoesNotInheritThem() {
        val settings = SettingsStore(File(root, "settings"))
        val config = QwenConfiguration(QwenNativeParameters(threads = 1, speaker = "aiden", instruction = "Speak calmly.", temperature = 0.7f,
            topK = 17, topP = 0.8f, repetitionPenalty = 1.2f, maxAudioTokens = 96, seed = 123456))
        settings.save("m", "engine1", 1, config.snapshot())
        assertEquals(config, QwenConfiguration.fromJson(settings.load("m", "engine1", 1)!!))
        rejected { settings.load("m", "engine2", 1) }
        val file = File(root, "settings").listFiles()!!.single { !it.name.endsWith(".active.json") }; val original = file.readBytes()
        rejected { settings.load("m", "engine1", 2) }; assertArrayEquals(original, file.readBytes())
    }
    @Test fun invalidRawEditsAreNotReplacedByOldAcceptedValues() {
        val form = QwenForm.from(QwenConfiguration(QwenNativeParameters(threads = 1)))
        rejected { form.copy(temperature = "NaN").parse() }
        rejected { form.copy(threads = "").parse() }
        rejected { form.copy(topK = "1.5").parse() }
        assertEquals(42, form.parse().native.seed)
    }
    @Test fun reopeningStoreDoesNotCleanAnOlderControllersActiveDraft() {
        store.begin().use { draft ->
            draft.file("audio.wav").writeText("live write")
            RunStore(File(root, "results"), File(root, "cache"))
            assertTrue(draft.file("audio.wav").isFile)
            draft.commit(JSONObject().put("status", "success").put("createdAtEpochMillis", 1), mapOf("audio" to "audio.wav"))
        }
        assertEquals(1, store.history().size)
    }
    @Test fun speechPreferencesRoundTripSeparatelyFromEngineParameters() {
        val preferences = SpeechPreferences(SpeechMode.SENTENCES, 5, 0.75f)
        assertEquals(preferences, SpeechPreferences.fromJson(preferences.snapshot()))
        rejected { SpeechPreferences(batchCount = 6).validate() }
        rejected { SpeechPreferences(playbackSpeed = Float.NaN).validate() }
    }
}
