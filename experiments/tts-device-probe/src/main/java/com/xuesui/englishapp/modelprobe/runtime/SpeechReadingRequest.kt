package com.xuesui.englishapp.modelprobe.runtime

/** Host-owned selection. Opening the reader never starts inference automatically. */
enum class SpeechReadingScope { FULL, PARAGRAPH, SENTENCE }

data class SpeechReadingRequest(
    val requestId: String,
    val title: String,
    val englishParagraphs: List<String>,
    val scope: SpeechReadingScope,
    val paragraphIndex: Int? = null,
    val sentenceText: String? = null,
)
