package com.xuesui.englishapp.study

internal fun findLatinWordAtOffset(text: String, offset: Int): String? {
    if (offset !in text.indices) return null

    val characters = buildList {
        var start = 0
        while (start < text.length) {
            val codePoint = text.codePointAt(start)
            val end = start + Character.charCount(codePoint)
            add(CodePointCharacter(start, end, codePoint))
            start = end
        }
    }
    val touchedIndex = characters.indexOfFirst { offset in it.start until it.end }
    if (touchedIndex < 0 || !characters.isWordCharacter(touchedIndex)) return null

    var first = touchedIndex
    while (first > 0 && characters.isWordCharacter(first - 1)) first--
    var last = touchedIndex
    while (last < characters.lastIndex && characters.isWordCharacter(last + 1)) last++

    return text.substring(characters[first].start, characters[last].end)
}

private data class CodePointCharacter(
    val start: Int,
    val end: Int,
    val codePoint: Int,
)

private fun List<CodePointCharacter>.isWordCharacter(index: Int): Boolean {
    val codePoint = this[index].codePoint
    if (codePoint.isLatinLetter()) return true
    if (codePoint !in WORD_CONNECTORS) return false
    return getOrNull(index - 1)?.codePoint?.isLatinLetter() == true &&
        getOrNull(index + 1)?.codePoint?.isLatinLetter() == true
}

private fun Int.isLatinLetter(): Boolean =
    Character.isLetter(this) && Character.UnicodeScript.of(this) == Character.UnicodeScript.LATIN

private val WORD_CONNECTORS = setOf('\''.code, '’'.code, '-'.code)
