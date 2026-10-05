package com.xuesui.englishapp.modelprobe.features.reading

import java.text.BreakIterator
import java.util.Locale
import com.xuesui.englishapp.modelprobe.core.storage.sha256

internal enum class ReadingScope { FULL, PARAGRAPH, SENTENCE }
internal data class ReadingSelection(val scope: ReadingScope = ReadingScope.FULL, val paragraph: Int? = null, val sentence: String? = null)
internal data class ReadingInput(val id: String, val title: String, val paragraphs: List<String>, val selection: ReadingSelection)
internal data class ReadingItem(val index: Int, val paragraph: Int, val firstSentence: Int, val lastSentence: Int,
    val start: Int, val end: Int, val text: String, val occurrenceCount: Int = 1) {
    val label get() = if (occurrenceCount > 1) "第${paragraph + 1}段 · 单句（重复文本） · 片段${index + 1}"
        else "第${paragraph + 1}段 · 第${firstSentence + 1}${if (lastSentence > firstSentence) "–${lastSentence + 1}" else ""}句 · 片段${index + 1}"
    val textHash get() = text.toByteArray(Charsets.UTF_8).sha256()
}
internal data class ReadingPlan(val input: ReadingInput, val items: List<ReadingItem>)

/** Exact source slices, bounded at sentence/word boundaries; never rewrite narration. */
internal object ReadingPlanner {
    const val VERSION = "english-paragraph-queue-v1"
    const val MAX_TEXT = 100_000
    const val MAX_PARAGRAPHS = 256
    const val MAX_ITEMS = 256
    const val CHUNK_CODEPOINTS = 800
    fun paragraphs(text: String): List<String> = text.split(Regex("(?:\\r?\\n)[\\t ]*(?:\\r?\\n)+")).filter { it.isNotBlank() }
    private fun ranges(text: String): List<IntRange> {
        val breaker = BreakIterator.getSentenceInstance(Locale.US).also { it.setText(text) }
        val result = mutableListOf<IntRange>()
        var start = breaker.first(); var end = breaker.next()
        while (end != BreakIterator.DONE) {
            if (text.substring(start, end).isNotBlank()) result += start until end
            start = end; end = breaker.next()
        }
        return result
    }
    fun sentences(text: String) = ranges(text).map { text.substring(it.first, it.last + 1).trim() }
    fun create(input: ReadingInput): ReadingPlan {
        require(input.id.isNotBlank() && input.id.length <= 256) { "朗读请求ID无效" }
        require(input.title.length <= 1024) { "文章标题过长" }
        require(input.paragraphs.size in 1..MAX_PARAGRAPHS && input.paragraphs.sumOf { it.length.toLong() } <= MAX_TEXT) { "请提供1至256个英文段落，总长度不超过100000字符" }
        input.paragraphs.forEach { text ->
            require(text.isNotBlank() && '\u0000' !in text) { "英文段落为空或含无效字符" }
            require(!text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) { "朗读正文含中文，请仅传入英文段落；中文个性化要求应填入语气指令" }
        }
        val selection = input.selection
        val indexes = if (selection.scope == ReadingScope.FULL) input.paragraphs.indices.toList() else {
            val index = selection.paragraph
            require(index != null && index in input.paragraphs.indices) { "所选段落越界，未改为全文朗读" }
            listOf(index)
        }
        val items = mutableListOf<ReadingItem>()
        indexes.forEach { p ->
            val text = input.paragraphs[p]
            val sentenceRanges = ranges(text)
            var from = 0; var until = text.length
            var occurrenceCount = 1
            if (selection.scope == ReadingScope.SENTENCE) {
                val requested = selection.sentence?.trim()
                require(!requested.isNullOrBlank()) { "请选择具体英文句子" }
                from = text.indexOf(requested)
                require(from >= 0) { "所选句子不属于指定段落，未回退到其他范围" }
                until = from + requested.length
                var match = text.indexOf(requested, from + 1)
                while (match >= 0) { occurrenceCount++; match = text.indexOf(requested, match + 1) }
            }
            while (from < until) {
                val remaining = text.codePointCount(from, until)
                var end = if (remaining <= CHUNK_CODEPOINTS) until else text.offsetByCodePoints(from, CHUNK_CODEPOINTS)
                if (end < until) {
                    val sentenceEnd = sentenceRanges.map { it.last + 1 }.lastOrNull { it > from && it <= end }
                    if (sentenceEnd != null) end = sentenceEnd
                    else {
                        val whitespace = (end - 1 downTo from + 1).firstOrNull { text[it].isWhitespace() }
                        if (whitespace != null) end = whitespace + 1
                    }
                }
                val part = text.substring(from, end)
                if (part.isNotBlank()) {
                    val touched = sentenceRanges.indices.filter { sentenceRanges[it].first < end && sentenceRanges[it].last >= from }
                    // The first match is only an extraction representative when text repeats;
                    // labels/reports must not claim it is the occurrence the host selected.
                    items += ReadingItem(items.size, p, touched.firstOrNull() ?: 0, touched.lastOrNull() ?: 0, from, end, part, occurrenceCount)
                    require(items.size <= MAX_ITEMS) { "全文超过256个生成片段，请缩小朗读范围" }
                }
                from = end
            }
        }
        require(items.isNotEmpty()) { "所选范围没有可朗读的英文" }
        return ReadingPlan(input.copy(paragraphs = input.paragraphs.toList()), items)
    }
}

internal enum class RequestOffer { OPENED, PENDING, DUPLICATE }
/** Main-thread inbox: one consumption per host request ID, no inference side effects. */
internal class ReadingInbox {
    private val consumed = mutableSetOf<String>()
    var pending: ReadingInput? = null; private set
    fun offer(input: ReadingInput, busy: Boolean): RequestOffer {
        if (input.id in consumed) return RequestOffer.DUPLICATE
        ReadingPlanner.create(input)
        consumed += input.id
        pending = if (busy) input.copy(paragraphs = input.paragraphs.toList()) else null
        return if (busy) RequestOffer.PENDING else RequestOffer.OPENED
    }
    fun takePending(busy: Boolean): ReadingInput? {
        if (busy) return null
        return pending.also { pending = null }
    }
    fun dismiss() { pending = null }
}
