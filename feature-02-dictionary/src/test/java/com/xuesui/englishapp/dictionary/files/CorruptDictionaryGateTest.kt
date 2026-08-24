package com.xuesui.englishapp.dictionary.files

import com.xuesui.englishapp.dictionary.engine.MdictEngineException
import com.xuesui.englishapp.dictionary.engine.MdictSource
import com.xuesui.englishapp.dictionary.engine.PureKotlinMdictEngine
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CorruptDictionaryGateTest {
    @Test
    fun corruptMdxFailsFormatProbeAndRollsBackStaging() {
        val root = Files.createTempDirectory("dictionary-corrupt-gate-").toFile()
        val staging = root.resolve("importing").apply { mkdirs() }
        val broken = staging.resolve("broken.mdx").apply { writeText("not an mdict") }
        try {
            assertThrows(MdictEngineException::class.java) {
                runBlocking {
                    withStagingRollback(staging) {
                        PureKotlinMdictEngine.open(MdictSource(broken)).use { }
                    }
                }
            }
            assertFalse(staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

