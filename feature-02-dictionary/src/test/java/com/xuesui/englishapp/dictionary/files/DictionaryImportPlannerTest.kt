package com.xuesui.englishapp.dictionary.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryImportPlannerTest {
    @Test
    fun pairsMdxWithCaseInsensitiveNumberedMddVolumes() {
        val plans = DictionaryImportPlanner.plan(
            listOf(
                document("main.MDX"),
                document("main.2.mdd"),
                document("MAIN.MDD"),
                document("main.1.MDD"),
                document("orphan.mdd"),
            ),
        )

        assertEquals(1, plans.size)
        assertEquals(listOf("MAIN.MDD", "main.1.MDD", "main.2.mdd"), plans.single().resources.map { it.displayName })
    }

    @Test
    fun allowsMdxWithoutResourcesAndIgnoresOrphanMdd() {
        val plans = DictionaryImportPlanner.plan(listOf(document("solo.mdx"), document("other.mdd")))

        assertEquals("solo.mdx", plans.single().mdx.displayName)
        assertTrue(plans.single().resources.isEmpty())
    }

    @Test
    fun rejectsUnsafeNamesFromPlanning() {
        assertTrue(DictionaryImportPlanner.plan(listOf(document("../bad.mdx"))).isEmpty())
    }

    @Test
    fun rejectsDuplicateLogicalMddPartsIncludingImplicitAndZero() {
        assertThrows(IllegalArgumentException::class.java) {
            DictionaryImportPlanner.plan(
                listOf(document("main.mdx"), document("main.mdd"), document("MAIN.0.MDD")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DictionaryImportPlanner.plan(
                listOf(document("main.mdx"), document("main.1.mdd"), document("MAIN.1.MDD")),
            )
        }
    }

    private fun document(name: String) = SafDocument(name, name, null, 1)
}
