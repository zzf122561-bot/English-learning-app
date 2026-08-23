package com.xuesui.englishapp.study

data class StudyTextSize(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
)

object StudyFontScale {
    const val MIN_LEVEL = 1
    const val DEFAULT_LEVEL = 5
    const val MAX_LEVEL = 10

    private val fontSizes = intArrayOf(12, 14, 15, 16, 17, 19, 21, 23, 26, 30)
    private val lineHeights = intArrayOf(20, 23, 25, 27, 29, 32, 35, 38, 42, 47)

    fun normalize(level: Int): Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    fun sizeFor(level: Int): StudyTextSize {
        val index = normalize(level) - MIN_LEVEL
        return StudyTextSize(
            fontSizeSp = fontSizes[index],
            lineHeightSp = lineHeights[index],
        )
    }
}
