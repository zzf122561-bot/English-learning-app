package com.xuesui.englishapp

import org.junit.Assert.*
import org.junit.Test

class ReadingRouteTest {
    private val paragraphs = listOf("First sentence. Another sentence.", "A second paragraph.")

    @Test fun lateCloseCannotDismissANewerRequestAndRepeatedCloseIsSafe() {
        val latest = createReadingRoute("latest", "Book", paragraphs, null, null)!!
        assertSame(latest, closeReadingRoute(latest, "previous"))
        assertNull(closeReadingRoute(latest, "latest"))
        assertNull(closeReadingRoute(null, "latest"))
    }

    @Test fun fullReadingKeepsOrderAndText() {
        val route = createReadingRoute("full", "Book", paragraphs, null, null)!!
        assertEquals(ReadingScope.FULL, route.scope)
        assertEquals(paragraphs, route.englishParagraphs)
        assertNull(route.paragraphIndex)
    }

    @Test fun paragraphPreservesExactIndex() {
        val route = createReadingRoute("paragraph", "Book", paragraphs, 1, null)!!
        assertEquals(ReadingScope.PARAGRAPH, route.scope)
        assertEquals(1, route.paragraphIndex)
    }

    @Test fun sentencePreservesExactOriginalSlice() {
        val route = createReadingRoute("sentence", "Book", paragraphs, 0, "Another sentence.")!!
        assertEquals(ReadingScope.SENTENCE, route.scope)
        assertEquals("Another sentence.", route.sentenceText)
    }

    @Test fun invalidSelectionNeverFallsBackToFullDocument() {
        assertNull(createReadingRoute("x", "", paragraphs, -1, null))
        assertNull(createReadingRoute("x", "", paragraphs, 2, null))
        assertNull(createReadingRoute("x", "", paragraphs, null, "First sentence."))
        assertNull(createReadingRoute("x", "", paragraphs, 1, "First sentence."))
        assertNull(createReadingRoute("x", "", paragraphs, 0, " "))
    }

    @Test fun emptyRequestsAreRejected() {
        assertNull(createReadingRoute("", "", paragraphs, null, null))
        assertNull(createReadingRoute("x", "", emptyList(), null, null))
        assertNull(createReadingRoute("x", "", listOf(" "), null, null))
        assertNull(createReadingRoute("x", "", listOf(" ", "Good."), 0, null))
    }

    @Test fun requestOwnsItsParagraphSnapshot() {
        val input = paragraphs.toMutableList()
        val route = createReadingRoute("one", "Book", input, null, null)!!
        input[0] = "changed"
        assertEquals(paragraphs, route.englishParagraphs)
    }

    @Test fun sameTextWithNewIdentityIsANewRequest() {
        val first = createReadingRoute("one", "Book", paragraphs, null, null)!!
        val second = createReadingRoute("two", "Book", paragraphs, null, null)!!
        assertNotEquals(first.requestId, second.requestId)
        assertEquals(first.englishParagraphs, second.englishParagraphs)
    }
}
