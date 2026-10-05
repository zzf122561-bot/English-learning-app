package com.xuesui.englishapp

import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingScope
import org.junit.Assert.*
import org.junit.Test

class InlineReadingRequestTest {
    private val paragraphs = listOf("Hello. Hello.", "Another paragraph.")
    private fun request(doc: String = "word-memory:1", title: String = "Book", text: List<String> = paragraphs,
        index: Int? = null, sentence: String? = null) = createInlineReadingRequest(doc, title, text, index, sentence)

    @Test fun recompositionAndRecreationKeepIdentityWithoutSavingArticleInBundle() {
        val first = request()!!
        val recreated = request(text = paragraphs.toList())!!
        assertEquals(first, recreated)
        assertEquals(71, first.requestId.length)
        assertFalse(first.requestId.contains("Hello"))
    }

    @Test fun titleChangeKeepsIdentityButUpdatesDisplayTitle() {
        assertEquals(request()!!.requestId, request(title = "Renamed")!!.requestId)
        assertEquals("Renamed", request(title = "Renamed")!!.title)
    }

    @Test fun documentsBodyAndSelectionAreIsolated() {
        val variants = listOf(request(), request(doc = "word-memory:2"),
            request(text = listOf("Changed.", paragraphs[1])), request(index = 0), request(index = 1),
            request(index = 0, sentence = "Hello."))
        assertEquals(variants.size, variants.map { it!!.requestId }.toSet().size)
        assertEquals(request()!!.requestId, request()!!.requestId)
    }

    @Test fun mapsAllScopesWithoutAlteringTextOrDuplicatingRepeatedSentence() {
        assertEquals(SpeechReadingScope.FULL, request()!!.scope)
        assertEquals(SpeechReadingScope.PARAGRAPH, request(index = 1)!!.scope)
        val single = request(index = 0, sentence = "Hello.")!!
        assertEquals(SpeechReadingScope.SENTENCE, single.scope)
        assertEquals("Hello.", single.sentenceText)
        assertEquals(0, single.paragraphIndex)
        assertEquals(paragraphs, single.englishParagraphs)
    }

    @Test fun invalidIdentityOrSelectionNeverExpandsToFullArticle() {
        assertNull(request(doc = ""))
        assertNull(request(doc = "bad\u0000id"))
        assertNull(request(doc = "a".repeat(1025)))
        assertNull(request(index = 100))
        assertNull(request(sentence = "Hello."))
        assertNull(request(index = 1, sentence = "Hello."))
        assertNull(request(text = emptyList()))
    }

    @Test fun snapshotsMutableInputAndFramesParagraphBoundaries() {
        val mutable = paragraphs.toMutableList()
        val first = request(text = mutable)!!
        mutable[0] = "Changed."
        assertEquals(paragraphs, first.englishParagraphs)
        assertNotEquals(request(text = listOf("a", "bc"))!!.requestId,
            request(text = listOf("ab", "c"))!!.requestId)
    }
}
