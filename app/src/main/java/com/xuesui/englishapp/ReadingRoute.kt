package com.xuesui.englishapp

/** Host-only navigation data. Never serialise entire articles into the Activity state Bundle. */
internal enum class ReadingScope { FULL, PARAGRAPH, SENTENCE }

internal data class ReadingRoute(
    val requestId: String,
    val title: String,
    val englishParagraphs: List<String>,
    val scope: ReadingScope,
    val paragraphIndex: Int?,
    val sentenceText: String?,
)

internal fun createReadingRoute(
    requestId: String,
    title: String,
    englishParagraphs: List<String>,
    paragraphIndex: Int?,
    sentenceText: String?,
): ReadingRoute? {
    if (requestId.isBlank() || englishParagraphs.isEmpty() || englishParagraphs.all { it.isBlank() }) return null
    if (paragraphIndex == null && sentenceText != null) return null
    if (paragraphIndex != null && (paragraphIndex !in englishParagraphs.indices || englishParagraphs[paragraphIndex].isBlank())) return null
    if (sentenceText != null && (sentenceText.isBlank() || !englishParagraphs[paragraphIndex!!].contains(sentenceText))) return null
    val scope = when {
        sentenceText != null -> ReadingScope.SENTENCE
        paragraphIndex != null -> ReadingScope.PARAGRAPH
        else -> ReadingScope.FULL
    }
    return ReadingRoute(requestId, title, englishParagraphs.toList(), scope, paragraphIndex, sentenceText)
}
