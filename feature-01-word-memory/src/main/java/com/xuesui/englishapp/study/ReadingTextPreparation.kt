package com.xuesui.englishapp.study

internal data class ReadingTargetMarker(
    val key: String,
    val english: String,
    val start: Int,
    val end: Int,
)

internal sealed interface ReadingParagraphPreparation {
    data class Ready(val text: String) : ReadingParagraphPreparation
    data class Invalid(val reason: String) : ReadingParagraphPreparation
}

internal fun prepareReadingParagraph(
    storedText: String,
    targets: List<ReadingTargetMarker>,
): ReadingParagraphPreparation {
    if (targets.isEmpty()) return ReadingParagraphPreparation.Ready(storedText)

    val sortedTargets = targets.sortedBy { it.start }
    val result = StringBuilder(storedText.length)
    var cursor = 0
    sortedTargets.forEachIndexed { index, target ->
        if (!VALID_TARGET_KEY.matches(target.key)) {
            return invalidMarker(index)
        }
        if (target.start !in 0 until storedText.length || target.end !in (target.start + 1)..storedText.length) {
            return invalidMarker(index)
        }
        if (target.start < cursor) {
            return invalidMarker(index)
        }

        val storedTarget = storedText.substring(target.start, target.end)
        val prefix = Regex(
            pattern = "^${Regex.escape(target.key)}\\.\\s+",
            option = RegexOption.IGNORE_CASE,
        ).find(storedTarget) ?: return invalidMarker(index)
        val spokenTarget = storedTarget.substring(prefix.range.last + 1)
        if (spokenTarget.isBlank() || spokenTarget != target.english) {
            return invalidMarker(index)
        }

        result.append(storedText, cursor, target.start)
        result.append(spokenTarget)
        cursor = target.end
    }
    result.append(storedText, cursor, storedText.length)
    return ReadingParagraphPreparation.Ready(result.toString())
}

private fun invalidMarker(index: Int) = ReadingParagraphPreparation.Invalid(
    reason = "第 ${index + 1} 个学习目标的编号位置无法确认。",
)

private val VALID_TARGET_KEY = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
