package com.xuesui.englishapp.ui

internal object WordMemoryLayout {
    const val COMPACT_WIDTH_DP = 360
    const val COMPACT_HORIZONTAL_PADDING_DP = 16
    const val STANDARD_HORIZONTAL_PADDING_DP = 20
    const val MAX_CONTENT_WIDTH_DP = 720

    fun horizontalPaddingDp(availableWidthDp: Int): Int =
        if (availableWidthDp < COMPACT_WIDTH_DP) {
            COMPACT_HORIZONTAL_PADDING_DP
        } else {
            STANDARD_HORIZONTAL_PADDING_DP
        }

    fun progressText(lastSegmentIndex: Int, segmentCount: Int): String {
        if (segmentCount <= 0) return "尚未开始"
        val currentSegment = lastSegmentIndex.coerceIn(0, segmentCount - 1) + 1
        return "上次读到第 $currentSegment 段 · 共 $segmentCount 段"
    }
}
