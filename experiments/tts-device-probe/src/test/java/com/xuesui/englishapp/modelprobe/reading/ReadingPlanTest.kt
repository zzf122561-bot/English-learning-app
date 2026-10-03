package com.xuesui.englishapp.modelprobe.reading

import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingRequest
import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingScope
import com.xuesui.englishapp.modelprobe.stage2.rejected
import org.junit.Assert.*
import org.junit.Test

class ReadingPlanTest {
    private val paragraphs = listOf("First paragraph. Another sentence!", "Second paragraph is independent.")
    private fun input(selection: ReadingSelection = ReadingSelection()) = ReadingInput("request-1", "Synthetic reading", paragraphs, selection)
    @Test fun fullAndParagraphKeepExactOrderAndSourceRanges() {
        val full = ReadingPlanner.create(input())
        assertEquals(paragraphs, full.items.map { it.text })
        assertEquals(listOf(0, 1), full.items.map { it.paragraph })
        val selected = ReadingPlanner.create(input(ReadingSelection(ReadingScope.PARAGRAPH, 1)))
        assertEquals(listOf(paragraphs[1]), selected.items.map { it.text })
        assertEquals(1, selected.items.single().paragraph)
    }
    @Test fun sentenceMustBelongToExactSelectedParagraphAndNeverFallsBack() {
        val sentence = "Another sentence!"
        val selected = ReadingPlanner.create(input(ReadingSelection(ReadingScope.SENTENCE, 0, sentence))).items.single()
        assertEquals(sentence, selected.text); assertEquals(1, selected.firstSentence)
        assertEquals(sentence, paragraphs[0].substring(selected.start, selected.end))
        listOf(ReadingSelection(ReadingScope.PARAGRAPH, -1), ReadingSelection(ReadingScope.PARAGRAPH, 9),
            ReadingSelection(ReadingScope.SENTENCE, null, sentence), ReadingSelection(ReadingScope.SENTENCE, 1, sentence),
            ReadingSelection(ReadingScope.SENTENCE, 0, " ")).forEach { rejected { ReadingPlanner.create(input(it)) } }
    }
    @Test fun longParagraphsAreBoundedWithoutDroppedWordsOrReorderedPunctuation() {
        val long = (1..70).joinToString(" ") { "Sentence $it contains careful punctuation, and stays in order." }
        val plan = ReadingPlanner.create(input().copy(paragraphs = listOf(long, paragraphs[1])))
        val first = plan.items.filter { it.paragraph == 0 }
        assertTrue(first.size > 1)
        assertEquals(long, first.joinToString("") { it.text })
        assertTrue(plan.items.all { it.text.codePointCount(0, it.text.length) <= ReadingPlanner.CHUNK_CODEPOINTS })
        assertEquals(paragraphs[1], plan.items.last().text)
        first.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
    }
    @Test fun longUnbrokenUnicodeSliceDoesNotSplitSurrogatePairs() {
        val text = "A" + "\uD83D\uDE00".repeat(1000) + "."
        val plan = ReadingPlanner.create(input().copy(paragraphs = listOf(text)))
        assertEquals(text, plan.items.joinToString("") { it.text })
        assertTrue(plan.items.none { Character.isLowSurrogate(it.text.first()) || Character.isHighSurrogate(it.text.last()) })
    }
    @Test fun rejectsUnsupportedBodiesAndKeepsStandaloneBlankUntilUserInputs() {
        listOf(emptyList(), listOf(" "), listOf("Hello\u0000"), listOf("English 中文"), List(257) { "A." }, listOf("a".repeat(100001)))
            .forEach { rejected { ReadingPlanner.create(input().copy(paragraphs = it)) } }
        assertEquals(listOf("One line.\nContinuation.", "Next paragraph."), ReadingPlanner.paragraphs("One line.\nContinuation.\n\nNext paragraph."))
        assertEquals("", ReadingUiState().text)
        assertNull(ReadingUiState().input)
    }
    @Test fun hostContractRetainsDeclaredFieldsAndNullableDefaults() {
        val request = SpeechReadingRequest("host-id", "Title", paragraphs, SpeechReadingScope.FULL)
        assertNull(request.paragraphIndex); assertNull(request.sentenceText)
        assertEquals(listOf("FULL", "PARAGRAPH", "SENTENCE"), SpeechReadingScope.entries.map { it.name })
        val sentence = request.copy(scope = SpeechReadingScope.SENTENCE, paragraphIndex = 0, sentenceText = "First paragraph.")
        assertEquals("First paragraph.", sentence.sentenceText)
    }
    @Test fun repeatedRequestIdsDoNotReplaceCurrentWorkAndBusyRequestRequiresSwitch() {
        val inbox = ReadingInbox()
        assertEquals(RequestOffer.OPENED, inbox.offer(input(), false))
        assertEquals(RequestOffer.DUPLICATE, inbox.offer(input().copy(paragraphs = listOf("Changed body.")), false))
        val mutable = paragraphs.toMutableList()
        assertEquals(RequestOffer.PENDING, inbox.offer(input().copy(id = "next-id", paragraphs = mutable), true))
        mutable[0] = "Mutated after dispatch."
        assertNull(inbox.takePending(true))
        val next = inbox.takePending(false)!!
        assertEquals(paragraphs, next.paragraphs); assertEquals("next-id", next.id)
        assertNull(inbox.takePending(false))
        assertEquals(RequestOffer.OPENED, inbox.offer(input().copy(id = "same-text-new-id"), false))
    }
    @Test fun sameActivityStandaloneAndArticleHaveSeparateDraftsAndRestoreWithoutGeneration() {
        val entries = ReadingEntryMemory()
        val standalone = ReadingUiState(text = "My own draft.", title = "Local")
        val article = ReadingUiState(text = paragraphs.joinToString("\n\n"), title = "Article", input = input())
        assertEquals(article, entries.article("request-1", standalone, article))
        assertEquals("request-1", entries.articleId)
        assertEquals(article, entries.article("request-1", article, null))
        val restored = entries.standalone(article)
        assertEquals("My own draft.", restored.text); assertNull(entries.articleId)
        assertEquals(article.text, entries.article("request-1", restored, null).text)
    }
}
