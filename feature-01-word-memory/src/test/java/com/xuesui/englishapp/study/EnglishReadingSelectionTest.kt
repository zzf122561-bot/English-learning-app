package com.xuesui.englishapp.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishReadingSelectionTest {
    private val paragraphs = listOf(
        "Dr. Smith paid 3.14 dollars. \"Really?\" Yes, he did.",
        "The final paragraph has no closing punctuation",
    )

    @Test
    fun fullArticlePreservesOriginalParagraphsAndOrder() {
        val request = buildReadingRequest("Notebook", paragraphs)

        assertEquals("Notebook", request?.title)
        assertEquals(paragraphs, request?.englishParagraphs)
        assertNull(request?.paragraphIndex)
        assertNull(request?.sentenceText)
    }

    @Test
    fun paragraphRequestUsesZeroBasedIndex() {
        val request = buildReadingRequest("Notebook", paragraphs, paragraphIndex = 1)

        assertEquals(1, request?.paragraphIndex)
        assertNull(request?.sentenceText)
    }

    @Test
    fun splitsAbbreviationsDecimalsQuotesAndFinalSentence() {
        assertEquals(
            listOf("Dr. Smith paid 3.14 dollars.", "\"Really?\"", "Yes, he did."),
            splitEnglishSentences(paragraphs[0]),
        )
        assertEquals(listOf(paragraphs[1]), splitEnglishSentences(paragraphs[1]))
    }

    @Test
    fun sentenceRequestMustBeAnExactSentenceFromTheParagraph() {
        val sentence = "\"Really?\""
        val request = buildReadingRequest("Notebook", paragraphs, 0, sentence)

        assertEquals(0, request?.paragraphIndex)
        assertEquals(sentence, request?.sentenceText)
        assertNull(buildReadingRequest("Notebook", paragraphs, 0, "Really"))
        assertNull(buildReadingRequest("Notebook", paragraphs, 0, "missing sentence"))
    }

    @Test
    fun rejectsEmptyChineseAndOutOfRangeRequests() {
        assertNull(buildReadingRequest("Notebook", emptyList()))
        assertNull(buildReadingRequest("Notebook", listOf("")))
        assertNull(buildReadingRequest("Notebook", listOf("中文译文。")))
        assertNull(buildReadingRequest("Notebook", paragraphs, 2))
        assertNull(buildReadingRequest("Notebook", paragraphs, -1))
        assertNull(buildReadingRequest("Notebook", paragraphs, null, "Sentence."))
        assertTrue(splitEnglishSentences("中文译文。").isEmpty())
    }

    @Test
    fun dispatcherEmitsOnlyValidatedPlainValues() {
        var received: ReadingRequest? = null
        val emitted = dispatchReadingRequest("Notebook", paragraphs, 0, "\"Really?\"") { title, texts, index, sentence ->
            received = ReadingRequest(title, texts, index, sentence)
        }
        val rejected = dispatchReadingRequest("Notebook", paragraphs, 0, "中文。") { _, _, _, _ ->
            error("Invalid request must not call back")
        }

        assertTrue(emitted)
        assertEquals(ReadingRequest("Notebook", paragraphs, 0, "\"Really?\""), received)
        assertFalse(rejected)
    }
}
