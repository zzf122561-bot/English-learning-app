package com.xuesui.englishapp.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryFeatureContractTest {
    @Test
    fun quickLookupCloseGateCallsHostExactlyOnce() {
        var closeCount = 0
        val gate = CloseRequestGate { closeCount++ }

        gate.request()
        gate.request()

        assertEquals(1, closeCount)
    }
}

