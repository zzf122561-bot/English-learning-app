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

    @Test
    fun viewModelCreatedByFullAcceptsLaterQuickLookupQuery() {
        val gate = InitialQueryGate(initialQuery = null)

        assertEquals(null, gate.offer(null, currentQuery = "existing full query"))
        assertEquals(null, gate.markReady(currentQuery = "existing full query"))
        assertEquals("apple", gate.offer("apple", currentQuery = "existing full query"))
        assertEquals(null, gate.offer(null, currentQuery = "apple"))
    }

    @Test
    fun consecutiveQuickLookupsSubmitEachDifferentQueryExactlyOnce() {
        val gate = InitialQueryGate(initialQuery = null)
        gate.markReady(currentQuery = "")

        assertEquals("apple", gate.offer("apple", currentQuery = ""))
        assertEquals(null, gate.offer("apple", currentQuery = "apple"))
        assertEquals("banana", gate.offer("banana", currentQuery = "apple"))
        assertEquals(null, gate.offer("banana", currentQuery = "banana"))
    }

    @Test
    fun quickLookupArrivingBeforeInstallationRunsAfterReady() {
        val gate = InitialQueryGate(initialQuery = "apple")

        assertEquals(null, gate.offer("apple", currentQuery = ""))
        assertEquals("apple", gate.markReady(currentQuery = ""))
        assertEquals(null, gate.offer("apple", currentQuery = "apple"))
    }

    @Test
    fun quickLookupNeverInheritsFullManagementAndFirstBackClosesOnce() {
        assertEquals(
            false,
            DictionaryEntryPolicy.isManaging(DictionaryPresentation.QUICK_LOOKUP, storedManaging = true),
        )
        assertEquals(
            true,
            DictionaryEntryPolicy.isManaging(DictionaryPresentation.FULL, storedManaging = true),
        )

        var closeCount = 0
        val gate = CloseRequestGate { closeCount++ }
        gate.request()
        gate.request()
        assertEquals(1, closeCount)
    }
}
