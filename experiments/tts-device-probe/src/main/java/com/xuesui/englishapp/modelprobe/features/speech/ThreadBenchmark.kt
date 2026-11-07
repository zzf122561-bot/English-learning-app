package com.xuesui.englishapp.modelprobe.features.speech

import com.xuesui.englishapp.modelprobe.contracts.speech.RunKind
import org.json.JSONArray
import org.json.JSONObject

internal data class ThreadTrial(val threads: Int, val kind: RunKind)
internal fun threadTrials(processors: Int): List<ThreadTrial> {
    require(processors > 0)
    val counts = listOf(4, 6, 8).filter { it <= processors }.ifEmpty { listOf(processors) }
    return counts.flatMap { listOf(ThreadTrial(it, RunKind.COLD), ThreadTrial(it, RunKind.WARM)) }
}

/** Copies measurements, not text. Null stays null: no fabricated timing/thermal zeroes. */
internal fun threadTrialSummary(report: JSONObject, trial: ThreadTrial): JSONObject {
    val result = JSONObject().put("threads", trial.threads).put("kind", trial.kind.name.lowercase())
    listOf("runId", "status", "textSha256", "engineVersion", "backend", "requested", "audio", "rtf", "loadMillis", "inferenceMillis", "wavWriteMillis", "elapsedMillis").forEach {
        result.put(it, report.opt(it) ?: JSONObject.NULL)
    }
    val segments = report.optJSONArray("effectiveSegments") ?: JSONArray()
    fun total(key: String, native: Boolean): Any {
        if (segments.length() == 0) return JSONObject.NULL
        var sum = 0L
        repeat(segments.length()) { index ->
            val segment = segments.getJSONObject(index)
            val source = if (native) segment.optJSONObject("native") else segment
            if (source == null || source.isNull(key)) return JSONObject.NULL
            sum += source.getLong(key)
        }
        return sum
    }
    result.put("tokenizeMillis", total("tokenizeMillis", false)).put("prefillMillis", total("prefillMillis", true))
        .put("tokenGenerationMillis", total("tokenGenerationMillis", true)).put("decodeMillis", total("decodeMillis", false))
    val measurement = report.optJSONObject("measurement")
    val samples = measurement?.optJSONArray("samples")
    val first = samples?.optJSONObject(0)
    val last = samples?.optJSONObject(samples.length() - 1)
    result.put("sampledPeakPssKiB", measurement?.opt("sampledPeakPssKiB") ?: JSONObject.NULL)
        .put("thermalStart", first?.opt("thermalStatus") ?: JSONObject.NULL)
        .put("thermalEnd", last?.opt("thermalStatus") ?: JSONObject.NULL)
        .put("batteryTemperatureStartC", first?.opt("batteryTemperatureC") ?: JSONObject.NULL)
        .put("batteryTemperatureEndC", last?.opt("batteryTemperatureC") ?: JSONObject.NULL)
    return result
}
