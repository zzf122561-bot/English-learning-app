package com.xuesui.englishapp.dictionary.data

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedDeletionGuardTest {
    @Test
    fun acceptsOnlyDirectVisibleDictionaryChildAndNeverRoot() {
        val parent = Files.createTempDirectory("imported-delete-").toFile()
        val root = parent.resolve("imported").apply { mkdirs() }
        try {
            assertFalse(ImportedDeletionGuard.isControlledDictionaryDirectory(root, root))
            assertTrue(ImportedDeletionGuard.isControlledDictionaryDirectory(root, root.resolve("dictionary-id")))
            assertFalse(ImportedDeletionGuard.isControlledDictionaryDirectory(root, root.resolve(".staging")))
            assertFalse(ImportedDeletionGuard.isControlledDictionaryDirectory(root, root.resolve("one/nested")))
            assertFalse(ImportedDeletionGuard.isControlledDictionaryDirectory(root, parent.resolve("outside")))
        } finally {
            parent.deleteRecursively()
        }
    }
}
