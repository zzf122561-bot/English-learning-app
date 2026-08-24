package com.xuesui.englishapp.dictionary.files

import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class StagingRollbackTest {
    @Test
    fun cancellationRemovesEntireStagingDirectory() {
        val root = Files.createTempDirectory("dictionary-cancel-").toFile()
        val staging = root.resolve("importing").apply { mkdirs() }
        staging.resolve("partial.mdx").writeText("partial")

        try {
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    withStagingRollback(staging) { throw CancellationException("cancelled") }
                }
            }
            assertFalse(staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptionRemovesEntireStagingDirectory() {
        val root = Files.createTempDirectory("dictionary-corrupt-").toFile()
        val staging = root.resolve("importing").apply { mkdirs() }
        staging.resolve("broken.mdx").writeText("broken")

        try {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    withStagingRollback(staging) { throw IllegalArgumentException("corrupt dictionary") }
                }
            }
            assertFalse(staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

