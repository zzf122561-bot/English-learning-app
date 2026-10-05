package com.xuesui.englishapp.modelprobe.features.reading

import com.xuesui.englishapp.modelprobe.contracts.speech.SpeechParameters
import com.xuesui.englishapp.modelprobe.core.storage.SavedRun
import com.xuesui.englishapp.modelprobe.core.storage.hashParts
import org.json.JSONObject

/** Host identity and logical range are independent of the text/voice inference cache. */
internal data class ReadingTarget(val documentId: String, val plan: ReadingPlan) {
    init {
        require(documentId.isNotBlank() && documentId.length <= 1024 && '\u0000' !in documentId) { "文章身份无效" }
        if (plan.input.selection.scope == ReadingScope.SENTENCE) {
            val paragraph = plan.input.paragraphs[plan.input.selection.paragraph!!]
            val sentence = plan.input.selection.sentence!!.trim()
            val first = paragraph.indexOf(sentence)
            require(paragraph.indexOf(sentence, first + 1) < 0) { "段内有重复的相同句子，无法确定选区；请选择该段朗读" }
        }
    }
    private val selection get() = plan.input.selection
    private val paragraph get() = if (selection.scope == ReadingScope.FULL) -1 else selection.paragraph!!
    private val sentenceRange get() = if (selection.scope == ReadingScope.SENTENCE)
        "${plan.items.first().firstSentence}:${plan.items.last().lastSentence}" else ""
    val key: String get() = hashParts(listOf("reading-retention-v1", documentId, selection.scope.name, paragraph.toString(), sentenceRange))
    val bodyKey: String get() = hashParts(listOf(ReadingPlanner.VERSION) + plan.items.map { it.text })
    val label: String get() = when (selection.scope) {
        ReadingScope.FULL -> "全文 · ${plan.input.paragraphs.size}段"
        ReadingScope.PARAGRAPH -> "第${paragraph + 1}段"
        ReadingScope.SENTENCE -> "第${paragraph + 1}段 · 第${plan.items.first().firstSentence + 1}句"
    }
    fun snapshot() = JSONObject().put("schemaVersion", 1).put("documentId", documentId)
        .put("scope", selection.scope.name).put("paragraphIndex", paragraph).put("sentenceRange", sentenceRange)
        .put("bodyKey", bodyKey).put("label", label)
    fun matches(run: SavedRun?, parameters: SpeechParameters, modelFingerprint: String, engineVersion: String): Boolean =
        run != null && run.status == "success" && run.report.optString("retentionKey") == key &&
            run.report.optJSONObject("retention")?.optString("bodyKey") == bodyKey &&
            run.report.optString("settingsKey") == settingsKey(parameters) &&
            run.report.optString("modelFingerprint") == modelFingerprint && run.report.optString("engineVersion") == engineVersion
    companion object {
        fun settingsKey(parameters: SpeechParameters) = hashParts(parameters.cacheParts() + parameters.randomSeed.toString())
    }
}
