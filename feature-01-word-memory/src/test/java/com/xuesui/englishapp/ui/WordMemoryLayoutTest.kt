package com.xuesui.englishapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WordMemoryLayoutTest {
    @Test
    fun compactAndStandardWidthsUseApprovedHorizontalPadding() {
        assertEquals(16, WordMemoryLayout.horizontalPaddingDp(320))
        assertEquals(20, WordMemoryLayout.horizontalPaddingDp(360))
    }

    @Test
    fun progressDescribesLastVisibleSegmentWithoutPretendingCompletion() {
        assertEquals("尚未开始", WordMemoryLayout.progressText(0, 0))
        assertEquals("上次读到第 1 段 · 共 118 段", WordMemoryLayout.progressText(-4, 118))
        assertEquals("上次读到第 118 段 · 共 118 段", WordMemoryLayout.progressText(999, 118))
    }
}
