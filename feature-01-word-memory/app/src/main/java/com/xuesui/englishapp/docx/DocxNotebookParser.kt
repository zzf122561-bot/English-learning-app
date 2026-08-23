package com.xuesui.englishapp.docx

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

class DocxNotebookParser {
    fun parse(
        input: InputStream,
        originalFileName: String,
        cancellationCheck: () -> Unit = {},
    ): ParsedNotebook {
        var coreTitle: String? = null
        var paragraphs: List<ParsedParagraph>? = null

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                cancellationCheck()
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "docProps/core.xml" -> coreTitle = parseCoreTitle(zip, cancellationCheck)
                    "word/document.xml" -> paragraphs = parseDocument(zip, cancellationCheck)
                }
                zip.closeEntry()
            }
        }

        val documentParagraphs = paragraphs
            ?: throw DocxFormatException(listOf("文档缺少 word/document.xml，可能不是有效的 DOCX 文件。"))
        return buildNotebook(documentParagraphs, coreTitle, originalFileName)
    }

    private fun buildNotebook(
        rawParagraphs: List<ParsedParagraph>,
        coreTitle: String?,
        originalFileName: String,
    ): ParsedNotebook {
        val paragraphs = rawParagraphs.filter { it.text.isNotBlank() }
        if (paragraphs.isEmpty()) {
            throw DocxFormatException(listOf("文档没有可识别的正文段落。"))
        }

        val titleIndex = findTitleIndex(paragraphs, coreTitle)
        if (titleIndex < 0) {
            throw DocxFormatException(listOf("未找到英文标题。请设置 Word 标题属性，或使用全加粗的英文标题段落。"))
        }
        val title = coreTitle?.trim().takeUnless { it.isNullOrEmpty() } ?: paragraphs[titleIndex].text.trim()
        val problems = mutableListOf<String>()
        val studySegments = mutableListOf<ParsedStudySegment>()
        val seenKeys = linkedSetOf<String>()
        var paragraphIndex = titleIndex + 1
        var targetPosition = 0

        while (paragraphIndex < paragraphs.size) {
            val englishParagraph = paragraphs[paragraphIndex]
            val englishTargets = extractTargets(englishParagraph)
            invalidBoldSegments(englishParagraph).forEach {
                problems += "英文段落 ${paragraphIndex + 1} 有无法识别的加粗内容：$it。加粗目标应写成“编号. 内容”。"
            }
            if (englishTargets.isEmpty()) {
                problems += "标题后的第 ${paragraphIndex + 1} 个非空段落不包含加粗编号目标项。"
                paragraphIndex += 1
                continue
            }
            val chineseParagraph = paragraphs.getOrNull(paragraphIndex + 1)
            if (chineseParagraph == null || !chineseParagraph.text.trimStart().startsWith(CHINESE_LABEL)) {
                problems += "英文段落 ${paragraphIndex + 1} 后缺少以 $CHINESE_LABEL 开头的中文译文段落。"
                paragraphIndex += 1
                continue
            }
            val chineseTargets = extractTargets(chineseParagraph)
            invalidBoldSegments(chineseParagraph).forEach {
                problems += "中文段落 ${paragraphIndex + 2} 有无法识别的加粗内容：$it。加粗目标应写成“编号. 内容”。"
            }
            val englishByKey = englishTargets.associateBy { it.key }
            val chineseByKey = chineseTargets.associateBy { it.key }

            duplicateKeys(englishTargets).forEach { problems += "英文段落存在重复编号：$it。" }
            duplicateKeys(chineseTargets).forEach { problems += "中文段落存在重复编号：$it。" }
            (englishByKey.keys - chineseByKey.keys).forEach { problems += "编号 $it 缺少中文释义。" }
            (chineseByKey.keys - englishByKey.keys).forEach { problems += "编号 $it 缺少英文目标。" }

            val parsedTargets = englishTargets.mapNotNull { english ->
                val chinese = chineseByKey[english.key] ?: return@mapNotNull null
                if (!seenKeys.add(english.key)) {
                    problems += "文档中编号 ${english.key} 重复出现。"
                    return@mapNotNull null
                }
                ParsedTarget(
                    key = english.key,
                    english = english.value,
                    chinese = chinese.value,
                    englishStart = english.start,
                    englishEnd = english.end,
                    chineseStart = chinese.start,
                    chineseEnd = chinese.end,
                    position = targetPosition++,
                )
            }
            studySegments += ParsedStudySegment(
                position = studySegments.size,
                englishText = englishParagraph.text,
                chineseText = chineseParagraph.text,
                targets = parsedTargets,
            )
            paragraphIndex += 2
        }

        if (studySegments.isEmpty()) {
            problems += "文档中没有形成任何有效的英中学习段。"
        }
        if (problems.isNotEmpty()) {
            throw DocxFormatException(problems.distinct())
        }
        return ParsedNotebook(title = title, originalFileName = originalFileName, segments = studySegments)
    }

    private fun findTitleIndex(paragraphs: List<ParsedParagraph>, coreTitle: String?): Int {
        val normalizedCoreTitle = coreTitle?.trim().orEmpty()
        if (normalizedCoreTitle.isNotEmpty()) {
            paragraphs.indexOfFirst { it.text.trim() == normalizedCoreTitle }.takeIf { it >= 0 }?.let { return it }
        }
        return paragraphs.indexOfFirst { paragraph ->
            extractTargets(paragraph).isEmpty() &&
                paragraph.segments.filter { it.text.isNotBlank() }.all { it.bold } &&
                paragraph.text.any { it.isLetter() } &&
                paragraph.text.none { isCjk(it) }
        }
    }

    private fun extractTargets(paragraph: ParsedParagraph): List<TargetSpan> {
        val result = mutableListOf<TargetSpan>()
        var offset = 0
        paragraph.segments.forEach { segment ->
            if (segment.bold && segment.text.isNotBlank()) {
                val trimmedStart = segment.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trimmedEnd = segment.text.indexOfLast { !it.isWhitespace() }.let { if (it < 0) segment.text.length else it + 1 }
                val value = segment.text.substring(trimmedStart, trimmedEnd)
                TARGET_PATTERN.matchEntire(value)?.let { match ->
                    result += TargetSpan(
                        key = match.groupValues[1].uppercase(Locale.ROOT),
                        value = match.groupValues[2].trim(),
                        start = offset + trimmedStart,
                        end = offset + trimmedEnd,
                    )
                }
            }
            offset += segment.text.length
        }
        return result
    }

    private fun duplicateKeys(targets: List<TargetSpan>): Set<String> =
        targets.groupingBy { it.key }.eachCount().filterValues { it > 1 }.keys

    private fun invalidBoldSegments(paragraph: ParsedParagraph): List<String> =
        paragraph.segments
            .filter { it.bold && it.text.isNotBlank() }
            .map { it.text.trim() }
            .filterNot { TARGET_PATTERN.matches(it) }

    private fun parseCoreTitle(input: InputStream, cancellationCheck: () -> Unit): String? {
        val handler = CoreTitleHandler(cancellationCheck)
        newSaxParser().parse(InputSource(NonClosingInputStream(input)), handler)
        return handler.title?.trim()
    }

    private fun parseDocument(input: InputStream, cancellationCheck: () -> Unit): List<ParsedParagraph> {
        val handler = DocumentHandler(cancellationCheck)
        newSaxParser().parse(InputSource(NonClosingInputStream(input)), handler)
        return handler.paragraphs
    }

    private fun newSaxParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
        setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
    }.newSAXParser()

    private fun SAXParserFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private data class TargetSpan(
        val key: String,
        val value: String,
        val start: Int,
        val end: Int,
    )

    private class CoreTitleHandler(
        private val cancellationCheck: () -> Unit,
    ) : DefaultHandler() {
        var title: String? = null
        private var inTitle = false
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            cancellationCheck()
            if (elementName(localName, qName) == "title") {
                inTitle = true
                text.clear()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inTitle) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (elementName(localName, qName) == "title" && inTitle) {
                title = text.toString()
                inTitle = false
            }
        }
    }

    private class DocumentHandler(
        private val cancellationCheck: () -> Unit,
    ) : DefaultHandler() {
        val paragraphs = mutableListOf<ParsedParagraph>()
        private var tableDepth = 0
        private var textBoxDepth = 0
        private var paragraphSegments: MutableList<StyledSegment>? = null
        private var inRun = false
        private var runBold = false
        private var inText = false
        private val runText = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            cancellationCheck()
            when (elementName(localName, qName)) {
                "tbl" -> tableDepth += 1
                "txbxContent" -> textBoxDepth += 1
                "p" -> if (!isExcluded()) paragraphSegments = mutableListOf()
                "r" -> if (paragraphSegments != null) {
                    inRun = true
                    runBold = false
                    runText.clear()
                }
                "b", "bCs" -> if (inRun) runBold = isOn(attributes)
                "t" -> if (inRun) inText = true
                "tab" -> if (inRun) runText.append('\t')
                "br" -> if (inRun) runText.append('\n')
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inRun && inText) runText.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (elementName(localName, qName)) {
                "t" -> inText = false
                "r" -> if (inRun) {
                    appendRun(runText.toString(), runBold)
                    inRun = false
                    runText.clear()
                }
                "p" -> paragraphSegments?.let { segments ->
                    val paragraph = ParsedParagraph(segments)
                    if (paragraph.text.isNotBlank()) paragraphs += paragraph
                    paragraphSegments = null
                }
                "txbxContent" -> textBoxDepth = (textBoxDepth - 1).coerceAtLeast(0)
                "tbl" -> tableDepth = (tableDepth - 1).coerceAtLeast(0)
            }
        }

        private fun appendRun(text: String, bold: Boolean) {
            if (text.isEmpty()) return
            val segments = paragraphSegments ?: return
            val last = segments.lastOrNull()
            if (last != null && last.bold == bold) {
                segments[segments.lastIndex] = last.copy(text = last.text + text)
            } else {
                segments += StyledSegment(text = text, bold = bold)
            }
        }

        private fun isExcluded(): Boolean = tableDepth > 0 || textBoxDepth > 0

        private fun isOn(attributes: Attributes?): Boolean {
            val value = attributes?.getValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                ?: attributes?.getValue("w:val")
                ?: attributes?.getValue("val")
                ?: return true
            return value.lowercase(Locale.ROOT) !in setOf("0", "false", "off", "no")
        }
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    companion object {
        private const val CHINESE_LABEL = "【中文译文】"
        private val TARGET_PATTERN = Regex("^([A-Za-z0-9][A-Za-z0-9_-]*)\\.\\s+(.+)$", setOf(RegexOption.DOT_MATCHES_ALL))

        private fun elementName(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

        private fun isCjk(char: Char): Boolean = char.code in 0x3400..0x9FFF
    }
}
