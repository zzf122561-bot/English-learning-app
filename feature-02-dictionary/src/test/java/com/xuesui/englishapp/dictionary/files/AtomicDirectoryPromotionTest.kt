package com.xuesui.englishapp.dictionary.files

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicDirectoryPromotionTest {
    @Test
    fun rollbackRestoresPreviousDirectoryAndRemovesPartialPromotion() {
        val root = Files.createTempDirectory("dictionary-promotion-").toFile()
        try {
            val target = root.resolve("target").apply { mkdirs() }
            target.resolve("value.txt").writeText("old")
            val staging = root.resolve("staging").apply { mkdirs() }
            staging.resolve("value.txt").writeText("new")
            val promotion = AtomicDirectoryPromotion(target)

            promotion.promote(staging)
            assertEquals("new", target.resolve("value.txt").readText())
            promotion.rollback()

            assertEquals("old", target.resolve("value.txt").readText())
            assertFalse(staging.exists())
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }

    @Test
    fun commitKeepsNewDirectoryAndRemovesBackup() {
        val root = Files.createTempDirectory("dictionary-promotion-").toFile()
        try {
            val target = root.resolve("target").apply { mkdirs() }
            target.resolve("value.txt").writeText("old")
            val staging = root.resolve("staging").apply { mkdirs() }
            staging.resolve("value.txt").writeText("new")
            AtomicDirectoryPromotion(target).apply { promote(staging); commit() }

            assertEquals("new", target.resolve("value.txt").readText())
            assertEquals(1, root.listFiles().orEmpty().size)
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }
}

