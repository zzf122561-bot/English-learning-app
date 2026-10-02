package com.xuesui.englishapp.modelprobe.engines.qwen3tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QwenParametersTest {
    @Test fun nonDefaultValuesAndExplicitRandomModeSurviveTheRequest() {
        val p = QwenNativeParameters(speaker = "aiden", threads = 1, instruction = "Speak calmly.",
            temperature = 0.7f, topK = 17, topP = 0.8f, repetitionPenalty = 1.2f, maxAudioTokens = 96, seed = -1)
        p.validate(8)
        assertEquals(-1L, p.seed)
        assertEquals("aiden", p.speaker)
        assertEquals(96, p.maxAudioTokens)
    }

    @Test fun invalidRequestsAreRejectedBeforeAnyNativeLibraryIsLoaded() {
        val base = QwenNativeParameters(threads = 1)
        val invalid = listOf(base.copy(temperature = Float.NaN), base.copy(temperature = Float.POSITIVE_INFINITY),
            base.copy(topP = 0f), base.copy(repetitionPenalty = 0.9f), base.copy(topK = -1),
            base.copy(maxAudioTokens = 0), base.copy(maxAudioTokens = 4097), base.copy(seed = -2),
            base.copy(seed = Int.MAX_VALUE.toLong() + 1), base.copy(threads = 0), base.copy(threads = 9),
            base.copy(languageId = 2055), base.copy(speaker = "unknown"),
            base.copy(instruction = "a".repeat(513)), base.copy(instruction = "a\u0000b"))
        invalid.forEach { p -> assertThrows(IllegalArgumentException::class.java) { p.validate(8) } }
    }

    @Test fun instructionLimitCountsUnicodeCodePoints() {
        QwenNativeParameters(threads = 1, instruction = "\uD83D\uDE42".repeat(512)).validate(8)
    }
}
