package com.xuesui.englishapp.modelprobe.features.speech

import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.storage.SavedRun

internal data class SpeechUiState(
    val busy: Boolean = false, val cancelling: Boolean = false, val message: String = "准备读取本地配置",
    val phase: String = "", val elapsedSeconds: Long = 0, val progress: Float? = null,
    val text: String = "", val sampleAvailable: Boolean = false, val sampleMessage: String = "",
    val manifestReady: Boolean = false, val manifestMessage: String = "", val installed: Boolean = false,
    val modelIssue: String = "",
    val loaded: Boolean = false, val instructionSupported: Boolean = true,
    val parametersValid: Boolean = true,
    val parameterError: String = "", val mode: SpeechMode = SpeechMode.PARAGRAPH, val batchCount: Int = 3,
    val cacheAllowed: Boolean = true,
    val playbackSpeed: Float = 1f, val playback: String = "未播放", val selected: SavedRun? = null,
    val history: List<SavedRun> = emptyList(), val talkerChosen: Boolean = false, val tokenizerChosen: Boolean = false,
    val hasSynthesis: Boolean = false,
)

internal interface SpeechActions {
    fun editText(text: String)
    fun restoreSample()
    fun setMode(mode: SpeechMode)
    fun setBatch(count: Int)
    fun saveSettings(reset: Boolean = false)
    fun install(fromImport: Boolean = false)
    fun verify()
    fun load()
    fun unload()
    fun deleteModel()
    fun generate(kind: RunKind, batch: Boolean = false)
    fun compareThreads()
    fun clearCache()
    fun select(id: String)
    fun selfCheck()
    fun cancel()
    fun setPlaybackSpeed(speed: Float)
    fun play(id: String)
    fun stopPlayback()
}

internal fun runStatusTitle(status: String) = when (status) {
    "success" -> "合成完成"; "cache" -> "复用缓存（未推理）"; "token_limit" -> "触及上限，可能截断"
    "cancelled" -> "取消完成，原生已退出"; "failed" -> "生成失败"; "self_check" -> "无模型自检通过"
    "benchmark" -> "线程对照完成"; "benchmark_partial" -> "线程对照未完成，已保留测量"; else -> status
}
