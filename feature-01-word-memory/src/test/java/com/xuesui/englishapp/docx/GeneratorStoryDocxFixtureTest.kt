package com.xuesui.englishapp.docx

import com.xuesui.englishapp.study.ReadingParagraphPreparation
import com.xuesui.englishapp.study.ReadingTargetMarker
import com.xuesui.englishapp.study.prepareReadingParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class GeneratorStoryDocxFixtureTest {
    @Test
    fun actualBuilderDocxMatchesExpectedJsonThroughParserAndCleaner() {
        val docxPath = configuredFixture("WORD_MEMORY_GENERATOR_FIXTURE_DOCX")
        val expectedPath = configuredFixture("WORD_MEMORY_GENERATOR_FIXTURE_EXPECTED")
        assertTrue("DOCX fixture does not exist: $docxPath", Files.isRegularFile(docxPath))
        assertTrue("Expected JSON does not exist: $expectedPath", Files.isRegularFile(expectedPath))

        val expectedJson = String(Files.readAllBytes(expectedPath), Charsets.UTF_8)
        val expectedHash = stringField(expectedJson, "document_sha256")
        assertEquals(expectedHash, sha256(docxPath))

        val expectedFileName = stringField(expectedJson, "document_filename")
        val parsed = Files.newInputStream(docxPath).use { input ->
            DocxNotebookParser().parse(input, expectedFileName)
        }
        val displayedParagraphsBeforeCleaning = buildList {
            add(parsed.title)
            parsed.segments.forEach { segment ->
                add(segment.englishText)
                add(segment.chineseText)
            }
        }
        val expectedDisplayedParagraphs = stringArray(expectedJson, "paragraphs")
        val expectedSpokenParagraphs = stringArray(expectedJson, "english_paragraphs_without_ids")
        val expectedTargets = targetRecords(expectedJson)

        val spokenParagraphs = parsed.segments.map { segment ->
            val prepared = prepareReadingParagraph(
                storedText = segment.englishText,
                targets = segment.targets.map { target ->
                    ReadingTargetMarker(
                        key = target.key,
                        english = target.english,
                        start = target.englishStart,
                        end = target.englishEnd,
                    )
                },
            )
            assertTrue("Builder fixture produced an invalid reading span", prepared is ReadingParagraphPreparation.Ready)
            (prepared as ReadingParagraphPreparation.Ready).text
        }

        assertEquals("story-docx-v1", stringField(expectedJson, "contract_version"))
        assertEquals(stringField(expectedJson, "title"), parsed.title)
        assertEquals(stringField(expectedJson, "core_title"), parsed.title)
        assertEquals(expectedFileName, parsed.originalFileName)
        assertEquals(2, parsed.segments.size)
        assertEquals(4, parsed.targetCount)
        assertEquals(expectedDisplayedParagraphs, displayedParagraphsBeforeCleaning)
        assertEquals(expectedSpokenParagraphs, spokenParagraphs)
        assertEquals(
            expectedTargets,
            parsed.segments.flatMap { segment ->
                segment.targets.map { target -> TargetRecord(target.key, target.english, target.chinese) }
            },
        )
        stringArray(expectedJson, "normal_number_assertions").forEach { number ->
            assertTrue("Expected normal number was removed: $number", spokenParagraphs.any { number in it })
        }

        val displayedParagraphsAfterCleaning = buildList {
            add(parsed.title)
            parsed.segments.forEach { segment ->
                add(segment.englishText)
                add(segment.chineseText)
            }
        }
        assertEquals(displayedParagraphsBeforeCleaning, displayedParagraphsAfterCleaning)
    }

    private fun configuredFixture(environmentName: String): Path {
        val configured = System.getenv(environmentName)
        assumeTrue("$environmentName is not configured for this directed integration test", !configured.isNullOrBlank())
        return Paths.get(configured!!)
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun targetRecords(json: String): List<TargetRecord> {
        val arrayBody = arrayBody(json, "target_records")
        return Regex("\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL).findAll(arrayBody).map { match ->
            val objectJson = match.groupValues[1]
            TargetRecord(
                id = stringField(objectJson, "id"),
                english = stringField(objectJson, "english"),
                chinese = stringField(objectJson, "chinese"),
            )
        }.toList()
    }

    private fun stringArray(json: String, name: String): List<String> =
        JSON_STRING.findAll(arrayBody(json, name)).map { decodeJsonString(it.groupValues[1]) }.toList()

    private fun arrayBody(json: String, name: String): String =
        Regex("\"${Regex.escape(name)}\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(json)
            ?.groupValues
            ?.get(1)
            ?: error("Missing JSON array: $name")

    private fun stringField(json: String, name: String): String =
        Regex("\"${Regex.escape(name)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.let(::decodeJsonString)
            ?: error("Missing JSON string: $name")

    private fun decodeJsonString(encoded: String): String = buildString(encoded.length) {
        var index = 0
        while (index < encoded.length) {
            val current = encoded[index++]
            if (current != '\\') {
                append(current)
                continue
            }
            require(index < encoded.length) { "Incomplete JSON escape" }
            when (val escaped = encoded[index++]) {
                '"', '\\', '/' -> append(escaped)
                'b' -> append('\b')
                'f' -> append('\u000C')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'u' -> {
                    require(index + 4 <= encoded.length) { "Incomplete JSON Unicode escape" }
                    append(encoded.substring(index, index + 4).toInt(16).toChar())
                    index += 4
                }
                else -> error("Unsupported JSON escape: $escaped")
            }
        }
    }

    private data class TargetRecord(
        val id: String,
        val english: String,
        val chinese: String,
    )

    companion object {
        private val JSON_STRING = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
    }
}
