package com.xuesui.englishapp.modelprobe.features.reading

import com.xuesui.englishapp.modelprobe.core.storage.SavedRun
import com.xuesui.englishapp.modelprobe.features.speech.PlaybackState

internal data class ReadingUiState(
    val title: String = "我的朗读", val text: String = "", val input: ReadingInput? = null,
    val selection: ReadingSelection = ReadingSelection(), val rows: List<ReadingRow> = emptyList(),
    val requestError: String = "", val pendingTitle: String? = null, val switching: Boolean = false,
    val selectedSession: SavedRun? = null, val playback: PlaybackState = PlaybackState(),
)
internal data class ReadingRow(val label: String, val preview: String = "", val status: String = "pending", val runId: String? = null, val hasAudio: Boolean = false)
internal interface ReadingActions {
    fun editReadingText(text: String)
    fun chooseReadingScope(scope: ReadingScope)
    fun chooseParagraph(index: Int)
    fun chooseSentence(sentence: String)
    fun generateReading(reuse: Boolean = true)
    fun switchReadingRequest()
    fun keepReadingRequest()
    fun selectReadingHistory(id: String)
    fun pausePlayback()
    fun resumePlayback()
}

/** Same-Activity routes share a runner but retain separate editable drafts and result selection. */
internal class ReadingEntryMemory {
    var articleId: String? = null; private set
    private var standalone = ReadingUiState()
    private val articles = mutableMapOf<String, ReadingUiState>()
    private fun remember(current: ReadingUiState) {
        val clean = current.copy(pendingTitle = null, switching = false, playback = PlaybackState())
        val id = articleId
        if (id == null) standalone = clean else articles[id] = clean
    }
    fun article(id: String, current: ReadingUiState, fresh: ReadingUiState?): ReadingUiState {
        if (articleId == id && fresh == null) return current
        remember(current); articleId = id
        return fresh ?: articles[id] ?: ReadingUiState(requestError = "此请求已处理，请返回文章重新发起朗读")
    }
    fun standalone(current: ReadingUiState): ReadingUiState {
        if (articleId == null) return current
        remember(current); articleId = null
        return standalone
    }
}
