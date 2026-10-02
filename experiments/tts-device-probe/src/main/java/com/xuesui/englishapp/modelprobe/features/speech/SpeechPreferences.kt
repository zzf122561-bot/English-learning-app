package com.xuesui.englishapp.modelprobe.features.speech

import com.xuesui.englishapp.modelprobe.contracts.speech.SpeechMode
import org.json.JSONObject

internal data class SpeechPreferences(val mode: SpeechMode = SpeechMode.PARAGRAPH, val batchCount: Int = 3, val playbackSpeed: Float = 1f) {
    fun validate() { require(batchCount in 1..5 && playbackSpeed.isFinite() && playbackSpeed in 0.5f..2f) }
    fun snapshot() = JSONObject().put("schemaVersion", 1).put("mode", mode.name).put("batchCount", batchCount).put("playbackSpeed", playbackSpeed.toDouble())
    companion object {
        fun fromJson(json: JSONObject): SpeechPreferences {
            require(json.getInt("schemaVersion") == 1)
            return SpeechPreferences(SpeechMode.valueOf(json.getString("mode")), json.getInt("batchCount"), json.getDouble("playbackSpeed").toFloat()).also { it.validate() }
        }
    }
}
