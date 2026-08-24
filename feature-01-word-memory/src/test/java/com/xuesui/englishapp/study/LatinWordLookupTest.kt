package com.xuesui.englishapp.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatinWordLookupTest {
    @Test
    fun returnsOrdinaryWordAtTouchedOffset() {
        assertEquals("context", findLatinWordAtOffset("Use context today", 5))
    }

    @Test
    fun preservesOriginalUppercase() {
        assertEquals("ReConnect", findLatinWordAtOffset("Try ReConnect now", 6))
    }

    @Test
    fun acceptsUnicodeLatinLetters() {
        assertEquals("naïve", findLatinWordAtOffset("A naïve idea", 4))
    }

    @Test
    fun includesStraightApostropheBetweenLatinLetters() {
        assertEquals("don't", findLatinWordAtOffset("I don't know", 3))
    }

    @Test
    fun includesCurlyApostropheBetweenLatinLetters() {
        assertEquals("we’re", findLatinWordAtOffset("Yes, we’re ready", 7))
    }

    @Test
    fun includesHyphensBetweenLatinLetters() {
        assertEquals("state-of-the-art", findLatinWordAtOffset("A state-of-the-art tool", 10))
    }

    @Test
    fun excludesPunctuationAtWordEdges() {
        assertEquals("Hello", findLatinWordAtOffset("(Hello!)", 1))
        assertNull(findLatinWordAtOffset("(Hello!)", 0))
        assertNull(findLatinWordAtOffset("(Hello!)", 7))
    }

    @Test
    fun rejectsWhitespace() {
        assertNull(findLatinWordAtOffset("two words", 3))
    }

    @Test
    fun rejectsChinese() {
        assertNull(findLatinWordAtOffset("中文 word", 0))
    }

    @Test
    fun rejectsDigits() {
        assertNull(findLatinWordAtOffset("word 123", 6))
    }

    @Test
    fun rejectsPurePunctuationAndEdgeConnectors() {
        assertNull(findLatinWordAtOffset("...", 1))
        assertNull(findLatinWordAtOffset("-word", 0))
        assertNull(findLatinWordAtOffset("word'", 4))
    }

    @Test
    fun handlesWordsAtStringBoundaries() {
        assertEquals("First", findLatinWordAtOffset("First middle Last", 0))
        assertEquals("Last", findLatinWordAtOffset("First middle Last", 16))
    }

    @Test
    fun rejectsOffsetsOutsideTheString() {
        assertNull(findLatinWordAtOffset("word", -1))
        assertNull(findLatinWordAtOffset("word", 4))
        assertNull(findLatinWordAtOffset("", 0))
    }
}
