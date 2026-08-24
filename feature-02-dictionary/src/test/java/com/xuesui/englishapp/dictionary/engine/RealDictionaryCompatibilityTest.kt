package com.xuesui.englishapp.dictionary.engine

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDictionaryCompatibilityTest {
    @Test
    fun bothAuthorizedDictionariesPassMilestone1bGate() {
        val projectRoot = locateProjectRoot()
        val dictionaries = listOf(
            DictionaryFiles(
                label = "collins",
                mdx = File(
                    projectRoot,
                    "dictionary-users/柯林斯双解学习词典/柯林斯高阶英汉双解学习词典（好看）.mdx",
                ),
                mdd = File(
                    projectRoot,
                    "dictionary-users/柯林斯双解学习词典/柯林斯高阶英汉双解学习词典（好看）.mdd",
                ),
            ),
            DictionaryFiles(
                label = "oxford9",
                mdx = File(projectRoot, "dictionary-users/牛津9英英(推荐)/Oxford ALD_9th_En-En.mdx"),
                mdd = File(projectRoot, "dictionary-users/牛津9英英(推荐)/Oxford ALD_9th_En-En.mdd"),
            ),
        )
        assertTrue("authorized dictionaries are missing", dictionaries.all { it.mdx.isFile && it.mdd.isFile })

        dictionaries.forEach { files ->
            PureKotlinMdictEngine.open(MdictSource(files.mdx, listOf(files.mdd))).use { engine ->
                assertTrue(engine.metadata.formatVersion.startsWith("2."))
                assertTrue("Encrypted=2 key index required", engine.metadata.encryptedIndex)
                assertTrue(engine.metadata.entryCount > 3)

                val lastIndex = (engine.metadata.entryCount - 1).toInt()
                val samples = listOf(0, lastIndex / 3, (lastIndex * 2) / 3, lastIndex)
                    .map { usableIndex(engine, it) }
                    .distinct()
                var partialPrefixVerified = false
                samples.forEach { index ->
                    val query = engine.headwordAt(index)
                    val entry = engine.exactLookup(query)
                    assertNotNull("exact lookup missed a real key", entry)
                    assertEquals(query, entry!!.headword)
                    val suggestions = engine.prefixLookup(query, 64)
                    assertTrue("prefix lookup missed sampled key", suggestions.any { it.value == query })
                    if (query.length > 1) {
                        partialPrefixVerified = partialPrefixVerified ||
                            engine.prefixLookup(query.dropLast(1), 256).any { it.value == query }
                    }
                    println(
                        "MDICT_GATE dictionary=${files.label} query=${query.safeLog()} hit=true " +
                            "bytes=${entry.html.toByteArray().size}",
                    )
                }
                assertTrue("partial prefix suggestion was not verified", partialPrefixVerified)

                val resourceKeys = engine.resourceKeys()
                assertTrue("MDD did not expose resource keys", resourceKeys.isNotEmpty())
                val cssKey = resourceKeys.firstOrNull { it.endsWith(".css", ignoreCase = true) }
                assertNotNull("MDD CSS resource missing for ${files.label}", cssKey)
                val css = engine.readResource(cssKey!!)
                assertNotNull("MDD CSS random read failed", css)
                assertEquals("text/css", css!!.mediaType)
                assertTrue(css.bytes.isNotEmpty())
                println("MDICT_GATE dictionary=${files.label} resource=text/css hit=true bytes=${css.bytes.size}")

                val imageKey = resourceKeys.firstOrNull {
                    it.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                        setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
                }
                assertNotNull("MDD image resource missing for ${files.label}", imageKey)
                val image = engine.readResource(imageKey!!)
                assertNotNull("MDD image random read failed", image)
                assertTrue(image!!.mediaType?.startsWith("image/") == true)
                assertTrue(image.bytes.isNotEmpty())
                assertEquals(image.mediaType, imageMagicType(image.bytes))
                println(
                    "MDICT_GATE dictionary=${files.label} resource=${image.mediaType} " +
                        "hit=true bytes=${image.bytes.size}",
                )

                val randomResourceIndexes = listOf(0, resourceKeys.lastIndex / 2, resourceKeys.lastIndex).distinct()
                randomResourceIndexes.forEach { index ->
                    val resource = engine.readResource(resourceKeys[index])
                    assertNotNull("random MDD resource read failed", resource)
                    println(
                        "MDICT_GATE dictionary=${files.label} resource=${resource!!.mediaType ?: "application/octet-stream"} " +
                            "hit=true bytes=${resource.bytes.size}",
                    )
                }

                val memory = engine.diagnostics
                assertFalse(memory.wholeFileRead)
                assertTrue(memory.maxSingleFileReadBytes.toLong() < memory.sourceBytes)
                assertTrue(memory.maxCompressedBlockBytes.toLong() < memory.sourceBytes)
                assertTrue(memory.maxDecompressedBlockBytes.toLong() < memory.sourceBytes)
                println(
                    "MDICT_MEMORY dictionary=${files.label} sourceBytes=${memory.sourceBytes} " +
                        "maxRead=${memory.maxSingleFileReadBytes} maxCompressed=${memory.maxCompressedBlockBytes} " +
                        "maxDecompressed=${memory.maxDecompressedBlockBytes} wholeFile=false",
                )
            }
        }
    }

    private fun imageMagicType(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        ) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte() -> "image/jpeg"
        else -> null
    }

    private fun String.safeLog(): String = replace(Regex("[\\r\\n\\t]"), " ")

    private fun usableIndex(engine: PureKotlinMdictEngine, requested: Int): Int {
        var index = requested
        while (index > 0 && engine.headwordAt(index).trim().trim('\uFEFF').isEmpty()) index--
        return index
    }

    private fun locateProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(3)
            .firstOrNull {
                File(
                    it,
                    "dictionary-users/柯林斯双解学习词典/柯林斯高阶英汉双解学习词典（好看）.mdx",
                ).isFile
            }
            ?: start
    }

    private data class DictionaryFiles(val label: String, val mdx: File, val mdd: File)
}
