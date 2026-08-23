package com.xuesui.englishapp.docx

data class StyledSegment(
    val text: String,
    val bold: Boolean,
)

data class ParsedParagraph(
    val segments: List<StyledSegment>,
) {
    val text: String = segments.joinToString(separator = "") { it.text }
}

data class ParsedTarget(
    val key: String,
    val english: String,
    val chinese: String,
    val englishStart: Int,
    val englishEnd: Int,
    val chineseStart: Int,
    val chineseEnd: Int,
    val position: Int,
)

data class ParsedStudySegment(
    val position: Int,
    val englishText: String,
    val chineseText: String,
    val targets: List<ParsedTarget>,
)

data class ParsedNotebook(
    val title: String,
    val originalFileName: String,
    val segments: List<ParsedStudySegment>,
) {
    val targetCount: Int = segments.sumOf { it.targets.size }
}

class DocxFormatException(
    val problems: List<String>,
) : IllegalArgumentException(problems.joinToString(separator = "\n"))

