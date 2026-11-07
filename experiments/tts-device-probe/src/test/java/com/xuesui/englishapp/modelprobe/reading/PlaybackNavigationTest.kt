package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.features.reading.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackNavigationTest {
    @Test fun fullRangeContinuesFromSelectedNaturalParagraph() {
        val intent = PlaybackIntent("full", listOf(0, 1, 2, 3))
        assertTrue(intent.select(2))
        assertEquals(3, intent.adjacent(1))
        intent.ended()
        assertEquals(3, intent.paragraph)
        intent.ended()
        assertNull(intent.paragraph)
        assertFalse(intent.desired)
    }
    @Test fun paragraphRangeCannotEscapeIntoOtherParagraphs() {
        val intent = PlaybackIntent("one", listOf(42))
        assertNull(intent.adjacent(-1)); assertNull(intent.adjacent(1))
        assertFalse(intent.select(43))
        assertEquals(42, intent.paragraph)
    }
    @Test fun existingOnlyNavigationKeepsOriginalSparseIndexes() {
        val intent = PlaybackIntent("existing", listOf(0, 3, 9))
        assertEquals(3, intent.adjacent(1))
        assertTrue(intent.select(3)); assertEquals(9, intent.adjacent(1))
        assertFalse(intent.select(4)); assertEquals(3, intent.paragraph)
    }
    @Test fun navigationWhilePausedDoesNotResume() {
        val intent = PlaybackIntent("full", listOf(0, 1))
        intent.pause(); intent.select(1)
        assertFalse(intent.desired)
        assertFalse(intent.ready(setOf(1)))
        intent.resume(); assertTrue(intent.ready(setOf(1)))
    }
    @Test fun missingParagraphNeverAdvancesOrResumesFromAvailabilityAlone() {
        val intent = PlaybackIntent("full", listOf(0, 1, 2))
        intent.select(1)
        assertFalse(intent.ready(setOf(0, 2)))
        assertEquals(1, intent.paragraph)
        intent.pause()
        assertFalse(intent.ready(setOf(0, 1, 2)))
        assertEquals(1, intent.paragraph)
    }
    @Test fun seekUsesRealPositiveDurationAndBounds() {
        assertNull(boundedSeek(20, -9223372036854775807L)); assertNull(boundedSeek(20, 0))
        assertEquals(0L, boundedSeek(-20, 100)); assertEquals(100L, boundedSeek(500, 100))
        assertEquals(40L, boundedSeek(40, 100))
    }
}
