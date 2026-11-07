package com.xuesui.englishapp.modelprobe.stage2

import com.xuesui.englishapp.modelprobe.contracts.speech.RunKind
import com.xuesui.englishapp.modelprobe.features.speech.*
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThreadBenchmarkTest {
    @Test fun trialsRespectActualProcessorLimitAndPairColdWithWarm() {
        assertEquals(listOf(4, 4, 6, 6, 8, 8), threadTrials(12).map { it.threads })
        assertEquals(listOf(4, 4, 6, 6), threadTrials(6).map { it.threads })
        assertEquals(listOf(2, 2), threadTrials(2).map { it.threads })
        threadTrials(8).chunked(2).forEach { assertEquals(listOf(RunKind.COLD, RunKind.WARM), it.map { trial -> trial.kind }) }
    }
    @Test fun unknownFailureMeasurementsStayNull() {
        val row = threadTrialSummary(JSONObject().put("status", "failed"), ThreadTrial(4, RunKind.COLD))
        listOf("rtf", "elapsedMillis", "prefillMillis", "decodeMillis", "sampledPeakPssKiB", "thermalEnd", "batteryTemperatureEndC").forEach {
            assertTrue(it, row.isNull(it))
        }
    }
    @Test fun summaryAggregatesStagesAndCopiesActualThermalValuesWithoutText() {
        val segments = JSONArray()
        repeat(2) { segments.put(JSONObject().put("tokenizeMillis", 3).put("decodeMillis", 7)
            .put("native", JSONObject().put("prefillMillis", 11).put("tokenGenerationMillis", 20))) }
        val report = JSONObject().put("status", "success").put("text", "PRIVATE fixture text, not an export field")
            .put("textSha256", "fixturehash").put("effectiveSegments", segments).put("rtf", 2.5)
            .put("measurement", JSONObject().put("sampledPeakPssKiB", 1200).put("samples", JSONArray()
                .put(JSONObject().put("batteryTemperatureC", 30.1).put("thermalStatus", 0))
                .put(JSONObject().put("batteryTemperatureC", 35.2).put("thermalStatus", 2))))
        val row = threadTrialSummary(report, ThreadTrial(6, RunKind.WARM))
        assertEquals(22L, row.getLong("prefillMillis")); assertEquals(40L, row.getLong("tokenGenerationMillis"))
        assertEquals(14L, row.getLong("decodeMillis")); assertEquals(6L, row.getLong("tokenizeMillis"))
        assertEquals(35.2, row.getDouble("batteryTemperatureEndC"), 0.0001)
        assertEquals(0, row.getInt("thermalStart")); assertEquals(2, row.getInt("thermalEnd"))
        assertFalse(row.has("text")); assertEquals("fixturehash", row.getString("textSha256"))
    }
    @Test fun missingOneSegmentTimingCannotBeReportedAsCompleteTotal() {
        val report = JSONObject().put("effectiveSegments", JSONArray()
            .put(JSONObject().put("native", JSONObject().put("prefillMillis", 3))).put(JSONObject()))
        assertTrue(threadTrialSummary(report, ThreadTrial(4, RunKind.WARM)).isNull("prefillMillis"))
    }
    @Test fun previousSettingsEnginesRemainExplicitlySupported() {
        assertTrue(QwenModel.COMPATIBLE_SETTINGS_ENGINES.contains("16bb5afc-ggml-af97976c-patch-ae30dc34-adapter3"))
        assertTrue(QwenModel.COMPATIBLE_SETTINGS_ENGINES.contains(QwenModel.PREVIOUS_ENGINE_VERSION))
        assertFalse(QwenModel.COMPATIBLE_SETTINGS_ENGINES.contains("unknown-engine"))
    }
}
