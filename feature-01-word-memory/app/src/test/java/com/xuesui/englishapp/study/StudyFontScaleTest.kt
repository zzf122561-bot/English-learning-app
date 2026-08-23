package com.xuesui.englishapp.study

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFontScaleTest {
    @Test
    fun mapsAllTenLevelsToTheSpecifiedFontSizesAndLineHeights() {
        val expected = listOf(
            StudyTextSize(12, 20),
            StudyTextSize(14, 23),
            StudyTextSize(15, 25),
            StudyTextSize(16, 27),
            StudyTextSize(17, 29),
            StudyTextSize(19, 32),
            StudyTextSize(21, 35),
            StudyTextSize(23, 38),
            StudyTextSize(26, 42),
            StudyTextSize(30, 47),
        )

        assertEquals(expected, (1..10).map(StudyFontScale::sizeFor))
    }

    @Test
    fun clampsLevelsOutsideTheSupportedRange() {
        assertEquals(1, StudyFontScale.normalize(Int.MIN_VALUE))
        assertEquals(10, StudyFontScale.normalize(Int.MAX_VALUE))
        assertEquals(StudyTextSize(12, 20), StudyFontScale.sizeFor(0))
        assertEquals(StudyTextSize(30, 47), StudyFontScale.sizeFor(11))
    }
}
