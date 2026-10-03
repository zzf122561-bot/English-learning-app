package com.xuesui.englishapp.study

internal data class ReadingRequest(
    val title: String,
    val englishParagraphs: List<String>,
    val paragraphIndex: Int?,
    val sentenceText: String?,
)

internal fun buildReadingRequest(
    title: String,
    englishParagraphs: List<String>,
    paragraphIndex: Int? = null,
    sentenceText: String? = null,
): ReadingRequest? {
    if (englishParagraphs.isEmpty() || englishParagraphs.any { !it.isEnglishReadingText() }) return null
    if (paragraphIndex == null) {
        if (sentenceText != null) return null
        return ReadingRequest(title, englishParagraphs.toList(), null, null)
    }

    val paragraph = englishParagraphs.getOrNull(paragraphIndex) ?: return null
    if (sentenceText == null) {
        return ReadingRequest(title, englishParagraphs.toList(), paragraphIndex, null)
    }
    if (!sentenceText.isEnglishReadingText()) return null
    if (sentenceText !in splitEnglishSentences(paragraph)) return null
    return ReadingRequest(title, englishParagraphs.toList(), paragraphIndex, sentenceText)
}

internal fun dispatchReadingRequest(
    title: String,
    englishParagraphs: List<String>,
    paragraphIndex: Int? = null,
    sentenceText: String? = null,
    onReadingRequested: (String, List<String>, Int?, String?) -> Unit,
): Boolean {
    val request = buildReadingRequest(title, englishParagraphs, paragraphIndex, sentenceText) ?: return false
    onReadingRequested(
        request.title,
        request.englishParagraphs,
        request.paragraphIndex,
        request.sentenceText,
    )
    return true
}

internal fun splitEnglishSentences(text: String): List<String> {
    if (!text.isEnglishReadingText()) return emptyList()
    val sentences = mutableListOf<String>()
    var sentenceStart = 0
    var index = 0
    while (index < text.length) {
        val character = text[index]
        if (character !in SENTENCE_TERMINATORS) {
            index++
            continue
        }
        if (character == '.' && shouldContinueAfterPeriod(text, index)) {
            index++
            continue
        }

        var boundaryEnd = index + 1
        while (boundaryEnd < text.length && text[boundaryEnd] in SENTENCE_TERMINATORS) boundaryEnd++
        while (boundaryEnd < text.length && text[boundaryEnd] in TRAILING_SENTENCE_CLOSERS) boundaryEnd++
        if (boundaryEnd < text.length && !text[boundaryEnd].isWhitespace()) {
            index++
            continue
        }
        addSentence(text, sentenceStart, boundaryEnd, sentences)
        sentenceStart = boundaryEnd
        while (sentenceStart < text.length && text[sentenceStart].isWhitespace()) sentenceStart++
        index = sentenceStart
    }
    addSentence(text, sentenceStart, text.length, sentences)
    return sentences
}

private fun shouldContinueAfterPeriod(text: String, periodIndex: Int): Boolean {
    val previous = text.getOrNull(periodIndex - 1)
    val next = text.getOrNull(periodIndex + 1)
    if (previous?.isDigit() == true && next?.isDigit() == true) return true
    if (next == '.') return true
    if (next != null && !next.isWhitespace() && next !in TRAILING_SENTENCE_CLOSERS) return true

    val token = periodToken(text, periodIndex).lowercase()
    if (token in NON_TERMINAL_ABBREVIATIONS) return true
    if (INITIALS_PATTERN.matches(token) && nextNonWhitespaceIndex(text, periodIndex + 1) != null) return true
    return false
}

private fun periodToken(text: String, periodIndex: Int): String {
    var start = periodIndex
    while (start > 0) {
        val candidate = text[start - 1]
        if (!candidate.isLetter() && candidate != '.') break
        start--
    }
    return text.substring(start, periodIndex + 1)
}

private fun nextNonWhitespaceIndex(text: String, start: Int): Int? {
    var index = start
    while (index < text.length && text[index].isWhitespace()) index++
    return index.takeIf { it < text.length }
}

private fun addSentence(text: String, start: Int, end: Int, destination: MutableList<String>) {
    var trimmedStart = start
    var trimmedEnd = end
    while (trimmedStart < trimmedEnd && text[trimmedStart].isWhitespace()) trimmedStart++
    while (trimmedEnd > trimmedStart && text[trimmedEnd - 1].isWhitespace()) trimmedEnd--
    if (trimmedStart >= trimmedEnd) return
    val sentence = text.substring(trimmedStart, trimmedEnd)
    if (sentence.isEnglishReadingText()) destination += sentence
}

private fun String.isEnglishReadingText(): Boolean {
    if (isBlank()) return false
    var hasLatinLetter = false
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN -> return false
            Character.UnicodeScript.LATIN -> if (Character.isLetter(codePoint)) hasLatinLetter = true
            else -> Unit
        }
        index += Character.charCount(codePoint)
    }
    return hasLatinLetter
}

private val SENTENCE_TERMINATORS = setOf('.', '!', '?')
private val TRAILING_SENTENCE_CLOSERS = setOf('"', '\'', '’', '”', ')', ']', '}')
private val NON_TERMINAL_ABBREVIATIONS = setOf(
    "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "st.", "mt.", "vs.",
    "e.g.", "i.e.", "a.m.", "p.m.",
)
private val INITIALS_PATTERN = Regex("(?:[a-z]\\.)+")
