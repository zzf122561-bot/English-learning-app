package com.xuesui.englishapp.dictionary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryFontScaleTest {
    @Test
    fun mapsEveryLevelToTheApprovedTenStepZoomScale() {
        assertEquals(
            listOf(71, 82, 88, 94, 100, 112, 124, 135, 153, 176),
            (1..10).map(DictionaryFontScale::textZoom),
        )
    }

    @Test
    fun clampsStoredOrSubmittedValuesAndDefaultsToLevelFive() {
        assertEquals(1, DictionaryFontScale.normalize(-10))
        assertEquals(10, DictionaryFontScale.normalize(99))
        assertEquals(100, DictionaryFontScale.textZoom(DictionaryFontScale.DEFAULT_LEVEL))
    }
}
