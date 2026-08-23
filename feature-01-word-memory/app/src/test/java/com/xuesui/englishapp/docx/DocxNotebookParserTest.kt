package com.xuesui.englishapp.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxNotebookParserTest {
    private val parser = DocxNotebookParser()

    @Test
    fun parsesAlternatingBilingualParagraphsAndBoldTargets() {
        val bytes = docx(
            title = "The Quiet Workshop",
            paragraphXml = buildString {
                append(paragraph(run("The Quiet Workshop", bold = true)))
                append(paragraph(run("At dawn, "), run("A0001. ", true), run("forge", true), run(" met "), run("A0002. patience", true), run(".")))
                append(paragraph(run("【中文译文】清晨，"), run("A0001. 锻造", true), run("遇见了"), run("A0002. 耐心", true), run("。")))
            },
        )

        val result = parser.parse(ByteArrayInputStream(bytes), "sample.docx")

        assertEquals("The Quiet Workshop", result.title)
        assertEquals(1, result.segments.size)
        assertEquals(2, result.targetCount)
        assertEquals(listOf("A0001", "A0002"), result.segments.single().targets.map { it.key })
        assertEquals("forge", result.segments.single().targets.first().english)
        assertEquals("锻造", result.segments.single().targets.first().chinese)
    }

    @Test
    fun rejectsMissingChineseTargetBeforeAnythingIsPersisted() {
        val bytes = docx(
            title = "Broken Pair",
            paragraphXml = paragraph(run("Broken Pair", true)) +
                paragraph(run("Remember "), run("A0001. lucid", true), run(" and "), run("A0002. brisk", true)) +
                paragraph(run("【中文译文】记住"), run("A0001. 清晰的", true)),
        )

        val error = runCatching { parser.parse(ByteArrayInputStream(bytes), "broken.docx") }.exceptionOrNull()

        assertTrue(error is DocxFormatException)
        assertTrue((error as DocxFormatException).problems.any { it.contains("A0002") && it.contains("缺少中文") })
    }

    @Test
    fun rejectsBoldContentWithoutTargetIdentifier() {
        val bytes = docx(
            title = "Wrong Bold",
            paragraphXml = paragraph(run("Wrong Bold", true)) +
                paragraph(run("This "), run("ordinary emphasis", true), run(" is invalid.")) +
                paragraph(run("【中文译文】这不符合模板。")),
        )

        val error = runCatching { parser.parse(ByteArrayInputStream(bytes), "wrong-bold.docx") }.exceptionOrNull()

        assertTrue(error is DocxFormatException)
        assertTrue((error as DocxFormatException).problems.any { it.contains("无法识别的加粗内容") })
    }

    @Test
    fun acceptsLargeDocumentsWithoutFixedTargetLimit() {
        val targetCount = 1500
        val paragraphXml = buildString {
            append(paragraph(run("A Long Local Atlas", true)))
            repeat(targetCount) { index ->
                val key = "A${(index + 1).toString().padStart(4, '0')}"
                append(paragraph(run("Clue "), run("$key. word${index + 1}", true), run(" remained in context.")))
                append(paragraph(run("【中文译文】线索"), run("$key. 释义${index + 1}", true), run("留在语境中。")))
            }
        }
        val bytes = docx("A Long Local Atlas", paragraphXml)

        val result = parser.parse(ByteArrayInputStream(bytes), "large.docx")

        assertEquals(targetCount, result.segments.size)
        assertEquals(targetCount, result.targetCount)
        assertEquals("A1500", result.segments.last().targets.single().key)
    }

    @Test
    fun pairsByOpaqueIdentifierWhenChineseOrderDiffers() {
        val bytes = docx(
            title = "Order Is Not Identity",
            paragraphXml = paragraph(run("Order Is Not Identity", true)) +
                paragraph(run("Use "), run("1048. reconnect", true), run(" before "), run("X-9. hold the line", true), run(".")) +
                paragraph(run("【中文译文】先是"), run("X-9. 坚守", true), run("，再是"), run("1048. 重新连接", true), run("。")),
        )

        val result = parser.parse(ByteArrayInputStream(bytes), "reordered.docx")

        assertEquals(listOf("1048", "X-9"), result.segments.single().targets.map { it.key })
        assertEquals("重新连接", result.segments.single().targets.first().chinese)
        assertEquals("坚守", result.segments.single().targets.last().chinese)
    }

    private fun docx(title: String, paragraphXml: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("docProps/core.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                    <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                        xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${xml(title)}</dc:title></cp:coreProperties>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>$paragraphXml</w:body>
                    </w:document>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun paragraph(vararg runs: String): String = "<w:p>${runs.joinToString("")}</w:p>"

    private fun run(text: String, bold: Boolean = false): String = buildString {
        append("<w:r>")
        if (bold) append("<w:rPr><w:b/></w:rPr>")
        append("<w:t xml:space=\"preserve\">")
        append(xml(text))
        append("</w:t></w:r>")
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
