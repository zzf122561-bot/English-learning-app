package com.xuesui.englishapp.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTextPreparationTest {
    @Test
    fun removesLetterNumberAndNumericLearningKeysByTheirStoredSpans() {
        val stored = "Use A0001. articulate, then 1048. reconnect."

        val result = prepareReadingParagraph(
            stored,
            listOf(
                marker(stored, "A0001", "articulate,", "A0001. articulate,"),
                marker(stored, "1048", "reconnect.", "1048. reconnect."),
            ),
        )

        assertEquals(ReadingParagraphPreparation.Ready("Use articulate, then reconnect."), result)
    }

    @Test
    fun preservesOrdinaryNumbersMoneyModelsAndDecimals() {
        val stored = "In 2026, model X200 cost $3.14 before A0001. change."

        val result = prepareReadingParagraph(
            stored,
            listOf(marker(stored, "A0001", "change.", "A0001. change.")),
        )

        assertEquals(
            ReadingParagraphPreparation.Ready("In 2026, model X200 cost $3.14 before change."),
            result,
        )
    }

    @Test
    fun removesOnlyMarkedOccurrenceWhenTargetWordRepeats() {
        val stored = "Change can mean A0007. change, while change remains ordinary."

        val result = prepareReadingParagraph(
            stored,
            listOf(marker(stored, "A0007", "change,", "A0007. change,")),
        )

        assertEquals(
            ReadingParagraphPreparation.Ready("Change can mean change, while change remains ordinary."),
            result,
        )
    }

    @Test
    fun cleansBeforeSentenceSplittingSoKeyPeriodIsNotASentence() {
        val stored = "Panel showed A0001. articulate, A0002. articular. Then it closed."
        val prepared = prepareReadingParagraph(
            stored,
            listOf(
                marker(stored, "A0001", "articulate,", "A0001. articulate,"),
                marker(stored, "A0002", "articular.", "A0002. articular."),
            ),
        ) as ReadingParagraphPreparation.Ready

        assertEquals(
            listOf("Panel showed articulate, articular.", "Then it closed."),
            splitEnglishSentences(prepared.text),
        )
        assertEquals(
            "Panel showed articulate, articular.",
            buildReadingRequest("Notebook", listOf(prepared.text), 0, "Panel showed articulate, articular.")?.sentenceText,
        )
    }

    @Test
    fun neverMutatesStoredDisplayText() {
        val stored = "Keep A0001. visible in the lesson."

        prepareReadingParagraph(
            stored,
            listOf(marker(stored, "A0001", "visible", "A0001. visible")),
        )

        assertEquals("Keep A0001. visible in the lesson.", stored)
    }

    @Test
    fun rejectsAmbiguousSpanInsteadOfGuessingOrDeletingDigits() {
        val stored = "In 2026 use A0001. articulate."
        val wrongStart = stored.indexOf("2026")

        val result = prepareReadingParagraph(
            stored,
            listOf(ReadingTargetMarker("A0001", "articulate.", wrongStart, wrongStart + 4)),
        )

        assertTrue(result is ReadingParagraphPreparation.Invalid)
    }

    @Test
    fun selectionNoticeExplainsThatGenerationIsTheNextStep() {
        assertEquals(
            "朗读范围已选为第 1 段，请点击下方“生成朗读”。",
            readingSelectionNotice(paragraphIndex = 0, sentenceText = null),
        )
    }

    private fun marker(
        stored: String,
        key: String,
        english: String,
        markedText: String,
    ): ReadingTargetMarker {
        val start = stored.indexOf(markedText)
        return ReadingTargetMarker(key, english, start, start + markedText.length)
    }
}
