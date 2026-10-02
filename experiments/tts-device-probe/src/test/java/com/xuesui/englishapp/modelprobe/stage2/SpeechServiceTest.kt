package com.xuesui.englishapp.modelprobe.stage2

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import com.xuesui.englishapp.modelprobe.core.storage.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.speech.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SpeechServiceTest {
    private val root = Files.createTempDirectory("probe-speech-test-").toFile()
    private val models = ModelStore(File(root, "models")) { Long.MAX_VALUE }
    private val descriptor = tinyDescriptor()
    private val engine = FakeSpeechEngine(descriptor)
    private val runs = RunStore(File(root, "results"), File(root, "cache"))
    private val service = SpeechService(engine, models, runs, FakeMetrics()) { }
    private val params = QwenConfiguration(QwenNativeParameters(threads = 1))
    private fun request(kind: RunKind = RunKind.COLD, text: String = "A small synthetic sentence.", mode: SpeechMode = SpeechMode.PARAGRAPH) = SpeechRequest(text, params, mode, kind)
    init { install(models, descriptor) }
    @After fun clean() { engine.unload(); root.deleteRecursively() }

    @Test fun wavHeaderDurationAndRtfUseActualSamplesAndGenerationPlusDecode() {
        val run = service.run(request(), WorkToken()); assertEquals("success", run.status)
        val bytes = run.artifact("audio").readBytes(); val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(24000, header.getInt(24)); assertEquals(480, header.getInt(40)); assertEquals(524, bytes.size)
        assertEquals(0.01, run.report.getJSONObject("audio").getDouble("durationSeconds"), 0.000001)
        assertEquals(2.5, run.report.getDouble("rtf"), 0.000001)
        assertTrue(run.report.isNull("firstPcmLatencyMillis"))
        assertFalse(run.report.toString().contains("A small synthetic sentence."))
    }
    @Test fun coldAndWarmAlwaysInferWhileCacheDoesNotReuseTimingAsNewMeasurement() {
        service.run(request(), WorkToken()); assertEquals(1, engine.loads); assertEquals(1, engine.calls)
        val cache = service.run(request(RunKind.CACHE), WorkToken())
        assertEquals("cache", cache.status); assertEquals(1, engine.calls); assertTrue(cache.report.isNull("rtf"))
        service.run(request(RunKind.WARM), WorkToken()); assertEquals(1, engine.loads); assertEquals(2, engine.calls)
        service.run(request(), WorkToken()); assertEquals(2, engine.loads); assertEquals(3, engine.calls)
    }
    @Test fun everyEffectiveParameterModelIdentityAndModeAffectCacheIdentity() {
        val base = request(); val baseKey = SpeechText.key(engine, base, params)
        val variants = listOf(params.native.copy(seed = 43), params.native.copy(instruction = "calm"), params.native.copy(speaker = "aiden"),
            params.native.copy(topK = 1), params.native.copy(topP = 0.8f), params.native.copy(temperature = 0.8f),
            params.native.copy(repetitionPenalty = 1.1f), params.native.copy(maxAudioTokens = 100), params.native.copy(threads = 2))
        variants.forEach { assertNotEquals(baseKey, SpeechText.key(engine, base, QwenConfiguration(it))) }
        assertNotEquals(baseKey, SpeechText.key(engine, base.copy(mode = SpeechMode.SENTENCES), params))
        assertNotEquals(baseKey, SpeechText.key(engine, base.copy(text = base.text + " "), params))
        val other = FakeSpeechEngine(descriptor.copy(engineVersion = "different-patch"))
        assertNotEquals(baseKey, SpeechText.key(other, base, params))
    }
    @Test fun cancellationRecordsFailureWithoutPcmAndReleasesLeaseBeforeNewRun() {
        engine.action = { it.cancel() }
        val cancelled = service.run(request(), WorkToken())
        assertEquals("cancelled", cancelled.status); assertEquals(0, cancelled.report.getJSONArray("artifacts").length())
        assertFalse(File(cancelled.directory, "audio.wav").exists())
        assertFalse(engine.loaded)
        engine.action = null
        assertEquals("success", service.run(request(), WorkToken()).status)
    }
    @Test fun sentenceRunsPreserveOrderAndRecordDifferentDeterministicSeeds() {
        val run = service.run(request(text = "First sentence. Second sentence!", mode = SpeechMode.SENTENCES), WorkToken())
        assertEquals("success", run.status); assertEquals(2, engine.calls)
        assertEquals(listOf(42L, 43L), engine.requests.map { (it as QwenConfiguration).native.seed })
        assertEquals(480, run.report.getJSONObject("audio").getLong("samples"))
    }
    @Test fun tokenLimitIsNotACompleteOrCacheableResult() {
        engine.limit = true
        val input = request(text = "First sentence. Second sentence!", mode = SpeechMode.SENTENCES)
        val run = service.run(input, WorkToken())
        assertEquals("token_limit", run.status); assertEquals(1, engine.calls)
        assertNull(runs.cached(SpeechText.key(engine, input, params)))
    }
    @Test fun missingSampleNeverFallsBackToInventedTextAndRandomSeedIsExplicit() {
        rejected { SpeechText.sample { throw java.io.FileNotFoundException("synthetic missing sample") } }
        rejected { SpeechText.sample { "中文".byteInputStream() } }
        val random = QwenConfiguration(params.native.copy(seed = -1)).resolved()
        assertFalse(random.randomSeed); assertTrue(random.native.seed >= 0)
    }
    @Test fun warmBatchKeepsRandomSeedFixedAndStopsAfterCancelledRun() {
        service.load(1, WorkToken())
        val random = request(RunKind.WARM).copy(parameters = QwenConfiguration(params.native.copy(seed = -1)))
        val saved = mutableListOf<SavedRun>()
        service.runBatch(random, 3, WorkToken()) { run, _ -> saved.add(run) }
        assertEquals(3, saved.size)
        assertEquals(1, engine.requests.map { (it as QwenConfiguration).native.seed }.toSet().size)
        assertTrue(saved.all { it.report.getJSONObject("requested").getLong("seed") == -1L })
        engine.action = { it.cancel() }
        var callbacks = 0
        service.runBatch(request(RunKind.WARM), 3, WorkToken()) { run, _ -> assertEquals("cancelled", run.status); callbacks++ }
        assertEquals(1, callbacks)
    }
    @Test fun failedNativeCallPreservesOnlyItsReportedEffectiveParametersWithoutAudio() {
        val applied = QwenConfiguration(params.native.copy(seed = 123))
        engine.action = { throw SpeechExecutionFailure(true, applied, "Synthetic native cancellation") }
        val result = service.run(request(), WorkToken())
        assertEquals("cancelled", result.status)
        assertEquals(123, result.report.getJSONObject("failedSegmentEffective").getLong("seed"))
        assertEquals(0, result.report.getJSONArray("artifacts").length())
        assertEquals(42, result.report.getJSONObject("requested").getLong("seed"))
    }
}
