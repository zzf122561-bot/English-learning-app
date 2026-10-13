package com.xuesui.englishapp

import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingScope
import org.junit.Assert.*
import org.junit.Test

class InlineReadingRequestTest {
    private val paragraphs = listOf("Hello. Hello.", "Another paragraph.")
    private fun request(doc: String = "word-memory:1", title: String = "Book", text: List<String> = paragraphs,
        index: Int? = null, sentence: String? = null) = createInlineReadingRequest(doc, title, text, index, sentence)

    @Test fun directPlaybackMapsToolbarAndParagraphWithoutSentenceSelection() {
        val full = createPlaybackReadingRequest("word-memory:1", "Book", paragraphs, null)!!
        val first = createPlaybackReadingRequest("word-memory:1", "Book", paragraphs, 0)!!
        val second = createPlaybackReadingRequest("word-memory:1", "Book", paragraphs, 1)!!
        assertEquals(SpeechReadingScope.FULL, full.scope)
        assertNull(full.paragraphIndex)
        assertEquals(SpeechReadingScope.PARAGRAPH, first.scope)
        assertEquals(0, first.paragraphIndex)
        assertEquals(1, second.paragraphIndex)
        assertNull(first.sentenceText)
        assertEquals(request(index = 0), first)
        assertNotEquals(first.requestId, second.requestId)
    }

    @Test fun directPlaybackRejectsInvalidContextAndKeepsCleanNumericContent() {
        assertNull(createPlaybackReadingRequest("word-memory:1", "Book", paragraphs, -1))
        assertNull(createPlaybackReadingRequest("word-memory:1", "Book", paragraphs, 2))
        assertNull(createPlaybackReadingRequest("", "Book", paragraphs, null))
        val clean = listOf("Learners pay 12.50 in 2026.")
        val result = createPlaybackReadingRequest("word-memory:42", "Book", clean, 0)!!
        assertEquals(clean, result.englishParagraphs)
        assertEquals(result.requestId,
            createPlaybackReadingRequest("word-memory:42", "Renamed", clean, 0)!!.requestId)
    }

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

    @Test fun cleanedReadingTextChangesContentIdentityWithoutChangingDocumentScope() {
        val old = request(text = listOf("A0001. Learners pay 12.50 in 2026."), index = 0)!!
        val clean = request(text = listOf("Learners pay 12.50 in 2026."), index = 0)!!
        assertNotEquals(old.requestId, clean.requestId)
        assertEquals(old.paragraphIndex, clean.paragraphIndex)
        assertEquals(SpeechReadingScope.PARAGRAPH, clean.scope)
        assertEquals("Learners pay 12.50 in 2026.", clean.englishParagraphs.single())
        assertEquals(clean.requestId, request(title = "Renamed", text = clean.englishParagraphs, index = 0)!!.requestId)
    }

    @Test fun paragraphRequestPreservesIndexDespiteUnselectedInvalidText() {
        val selected = request(text = listOf("A valid first paragraph.", "", "中文备注"), index = 0)!!
        assertEquals(0, selected.paragraphIndex)
        assertEquals(3, selected.englishParagraphs.size)
        assertEquals(SpeechReadingScope.PARAGRAPH, selected.scope)
        assertEquals("A valid first paragraph.", selected.englishParagraphs[0])
    }
}
