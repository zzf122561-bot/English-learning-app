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
    const val VERSION = "english-budget-queue-v2"
    const val MAX_TEXT = 100_000
    const val MAX_PARAGRAPHS = 256
    const val MAX_ITEMS = 1024
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
    fun create(input: ReadingInput, chunkCodePoints: Int = CHUNK_CODEPOINTS): ReadingPlan {
        require(chunkCodePoints in 1..CHUNK_CODEPOINTS)
        require(input.id.isNotBlank() && input.id.length <= 256) { "朗读请求ID无效" }
        require(input.title.length <= 1024) { "文章标题过长" }
        require(input.paragraphs.size in 1..MAX_PARAGRAPHS && input.paragraphs.sumOf { it.length.toLong() } <= MAX_TEXT) { "请提供1至256个英文段落，总长度不超过100000字符" }
        val selection = input.selection
        val indexes = if (selection.scope == ReadingScope.FULL) input.paragraphs.indices.toList() else {
            val index = selection.paragraph
            require(index != null && index in input.paragraphs.indices) { "所选段落越界，未改为全文朗读" }
            listOf(index)
        }
        val items = mutableListOf<ReadingItem>()
        indexes.forEach { p ->
            val text = input.paragraphs[p]
            require(text.isNotBlank() && '\u0000' !in text) { "第${p + 1}段为空或含无效字符" }
            require(!text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) { "第${p + 1}段含中文，请仅传入英文朗读正文" }
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
                var end = if (remaining <= chunkCodePoints) until else text.offsetByCodePoints(from, chunkCodePoints)
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
                    require(items.size <= MAX_ITEMS) { "当前预算需要超过1024个生成片段，请缩小朗读范围或调整生成预算" }
                }
                from = end
            }
        }
        require(items.isNotEmpty()) { "所选范围没有可朗读的英文" }
        return ReadingPlan(input.copy(paragraphs = input.paragraphs.toList()), items)
    }

    /** A failed attempt is replaced by two exact slices, never appended before its retry. */
    fun split(item: ReadingItem, paragraphText: String): List<ReadingItem>? {
        val size = item.text.codePointCount(0, item.text.length)
        if (size < 64) return null
        val low = item.text.offsetByCodePoints(0, size / 4)
        val high = item.text.offsetByCodePoints(0, size * 3 / 4)
        val middle = item.text.offsetByCodePoints(0, size / 2)
        val sentenceRanges = ranges(paragraphText)
        val sentences = sentenceRanges.map { it.last + 1 - item.start }.filter { it in low..high }
        val candidates = sentences.ifEmpty { (low..high).filter { it > 0 && item.text[it - 1].isWhitespace() } }
        val cut = candidates.filter { item.text.substring(0, it).isNotBlank() && item.text.substring(it).isNotBlank() }
            .minByOrNull { kotlin.math.abs(it - middle) } ?: return null
        return listOf(item.start to item.start + cut, item.start + cut to item.end).map { (start, end) ->
            val touched = sentenceRanges.indices.filter { sentenceRanges[it].first < end && sentenceRanges[it].last >= start }
            item.copy(start = start, end = end, text = paragraphText.substring(start, end),
                firstSentence = touched.firstOrNull() ?: item.firstSentence, lastSentence = touched.lastOrNull() ?: item.lastSentence)
        }
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
