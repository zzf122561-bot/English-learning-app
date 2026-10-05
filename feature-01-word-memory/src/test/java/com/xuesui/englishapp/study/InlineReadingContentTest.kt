package com.xuesui.englishapp.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineReadingContentTest {
    private val paragraphs = listOf(
        "First sentence. Second sentence.",
        "A separate paragraph.",
    )

    @Test
    fun documentIdentityIsStableAndNotebookScoped() {
        assertEquals("word-memory:42", wordMemoryDocumentId(42))
        assertEquals(wordMemoryDocumentId(42), wordMemoryDocumentId(42))
        assertNotEquals(wordMemoryDocumentId(42), wordMemoryDocumentId(43))
    }

    @Test
    fun inlineContentReceivesSelectionWithoutCallingLegacyCallback() {
        var legacyCalls = 0

        val request = deliverReadingRequest(
            title = "Notebook",
            englishParagraphs = paragraphs,
            paragraphIndex = 0,
            sentenceText = "Second sentence.",
            inlineContentAvailable = true,
        ) { _, _, _, _ -> legacyCalls++ }

        assertEquals(0, legacyCalls)
        assertEquals(0, request?.paragraphIndex)
        assertEquals("Second sentence.", request?.sentenceText)
    }

    @Test
    fun nullInlineContentKeepsLegacyCallbackBehavior() {
        var received: ReadingRequest? = null

        val request = deliverReadingRequest(
            title = "Notebook",
            englishParagraphs = paragraphs,
            paragraphIndex = 1,
            inlineContentAvailable = false,
        ) { title, texts, index, sentence ->
            received = ReadingRequest(title, texts, index, sentence)
        }

        assertEquals(request, received)
        assertEquals(1, received?.paragraphIndex)
        assertNull(received?.sentenceText)
    }

    @Test
    fun sameDocumentCanSwitchBetweenFullParagraphAndSentence() {
        val full = buildReadingRequest("Notebook", paragraphs)
        val paragraph = buildReadingRequest("Notebook", paragraphs, paragraphIndex = 0)
        val sentence = buildReadingRequest("Notebook", paragraphs, paragraphIndex = 0, sentenceText = "First sentence.")

        assertNull(full?.paragraphIndex)
        assertNull(full?.sentenceText)
        assertEquals(0, paragraph?.paragraphIndex)
        assertNull(paragraph?.sentenceText)
        assertEquals("First sentence.", sentence?.sentenceText)
        assertTrue(listOf(full, paragraph, sentence).all { it?.englishParagraphs == paragraphs })
    }

    @Test
    fun changedBodyRejectsStaleSentenceSoCallerCanResetToFull() {
        val restored = buildReadingRequest(
            title = "Notebook",
            englishParagraphs = listOf("Replacement sentence."),
            paragraphIndex = 0,
            sentenceText = "First sentence.",
        )

        assertNull(restored)
        assertEquals(
            ReadingRequest("Notebook", listOf("Replacement sentence."), null, null),
            buildReadingRequest("Notebook", listOf("Replacement sentence.")),
        )
    }
}
