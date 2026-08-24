package com.xuesui.englishapp.dictionary.engine

import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class RedirectGuardTest {
    @Test
    fun rejectsCaseInsensitiveResourceCycle() {
        val guard = RedirectGuard()
        guard.enter("/Audio/word.mp3")
        assertThrows(MdictEngineException::class.java) { guard.enter("\\audio\\WORD.mp3") }
    }

    @Test
    fun rejectsResourceRedirectBeyondMaximumDepth() {
        val guard = RedirectGuard(maxDepth = 3)
        guard.enter("one")
        guard.enter("two")
        guard.enter("three")
        assertThrows(MdictEngineException::class.java) { guard.enter("four") }
    }

    @Test
    fun normalizationDoesNotDependOnTurkishDeviceLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            val guard = RedirectGuard()
            guard.enter("I")
            assertThrows(MdictEngineException::class.java) { guard.enter("i") }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
