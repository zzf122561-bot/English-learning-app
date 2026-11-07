package com.xuesui.englishapp.modelprobe.engines.qwen3tts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Raw edit state is retained separately: an invalid edit never silently uses old values. */
internal data class QwenForm(
    val speaker: String, val instruction: String, val threads: String, val temperature: String,
    val topK: String, val topP: String, val repetition: String, val maxTokens: String, val seed: String,
) {
    fun parse() = QwenConfiguration(QwenNativeParameters(speaker = speaker, instruction = instruction,
        threads = threads.toInt(), temperature = temperature.toFloat(), topK = topK.toInt(), topP = topP.toFloat(),
        repetitionPenalty = repetition.toFloat(), maxAudioTokens = maxTokens.toInt(), seed = seed.toLong())).also { it.validate() }
    companion object {
        fun from(configuration: QwenConfiguration): QwenForm = configuration.native.let {
            QwenForm(it.speaker, it.instruction, it.threads.toString(), it.temperature.toString(), it.topK.toString(),
                it.topP.toString(), it.repetitionPenalty.toString(), it.maxAudioTokens.toString(), it.seed.toString())
        }
    }
}

@Composable
internal fun QwenSettings(form: QwenForm, enabled: Boolean, instructionEnabled: Boolean, onChange: (QwenForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("英语 · CPU", style = MaterialTheme.typography.titleMedium)
        Text("音色和语气属于模型设置。播放器速度单独调整，不改变生成音频。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ryan" to "Ryan", "aiden" to "Aiden").forEach { (id, title) ->
                FilterChip(selected = form.speaker == id, onClick = { onChange(form.copy(speaker = id)) }, label = { Text(title) }, enabled = enabled)
            }
        }
        OutlinedTextField(form.instruction, { onChange(form.copy(instruction = it)) }, Modifier.fillMaxWidth(),
            label = { Text("语气指令（可留空，最多512字符）") }, enabled = enabled && instructionEnabled, minLines = 2)
        if (!instructionEnabled) Text("载入后的模型未报告语气指令支持。")
        QwenAdvancedSettings(form, enabled, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QwenVoiceSettings(form: QwenForm, enabled: Boolean, instructionEnabled: Boolean, onChange: (QwenForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ryan" to "Ryan", "aiden" to "Aiden").forEach { (id, label) ->
                FilterChip(selected = form.speaker == id, onClick = { onChange(form.copy(speaker = id)) }, enabled = enabled, label = { Text(label) })
            }
        }
        Text("生成风格", style = MaterialTheme.typography.labelLarge)
        val styles = listOf("自然" to "", "慢速" to "Speak slowly and clearly, with natural pauses.", "轻快" to "Speak at a slightly faster, lively pace while staying clear.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            styles.forEach { (label, instruction) ->
                FilterChip(selected = form.instruction == instruction, onClick = { onChange(form.copy(instruction = instruction)) },
                    enabled = enabled && instructionEnabled, label = { Text(label) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onChange(form.copy(instruction = "Speak clearly and gently, at a slow pace suitable for English dictation.")) }, enabled = enabled && instructionEnabled) { Text("填入听写要求") }
            TextButton(onClick = { onChange(form.copy(instruction = "Speak in a warm, calm and gentle voice.")) }, enabled = enabled && instructionEnabled) { Text("填入温和要求") }
        }
        OutlinedTextField(form.instruction, { onChange(form.copy(instruction = it)) }, Modifier.fillMaxWidth(), minLines = 2,
            enabled = enabled && instructionEnabled, label = { Text("个性化语音要求（可编辑、可留空）") }, supportingText = { Text("例如清晰、温和、适合听写。要求原样传给模型，不改写英文正文。最多512个字符。") })
        Text("生成风格通过模型语气指令表达快慢，无法承诺精确数值语速。播放倍速单独调整。", style = MaterialTheme.typography.bodySmall)
        if (!instructionEnabled) Text("当前载入模型未提供语气指令能力。")
    }
}

@Composable
internal fun QwenAdvancedSettings(form: QwenForm, enabled: Boolean, onChange: (QwenForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumericField("CPU线程 · 1…${Runtime.getRuntime().availableProcessors()}", form.threads, enabled) { onChange(form.copy(threads = it)) }
        ParameterHint("CPU线程说明", ParameterHelp.threads)
        NumericField("采样温度 · 0…2（0选最高概率）", form.temperature, enabled) { onChange(form.copy(temperature = it)) }
        ParameterHint("温度说明", ParameterHelp.temperature)
        NumericField("Top-k · 0…2048（0关闭）", form.topK, enabled) { onChange(form.copy(topK = it)) }
        ParameterHint("Top-k说明", ParameterHelp.topK)
        NumericField("Top-p · 大于0且不超过1", form.topP, enabled) { onChange(form.copy(topP = it)) }
        ParameterHint("Top-p说明", ParameterHelp.topP)
        NumericField("重复惩罚 · 1…2", form.repetition, enabled) { onChange(form.copy(repetition = it)) }
        ParameterHint("重复惩罚说明", ParameterHelp.repetition)
        NumericField("最大音频帧 · 1…4096", form.maxTokens, enabled) { onChange(form.copy(maxTokens = it)) }
        ParameterHint("音频帧预算说明", ParameterHelp.frames)
        NumericField("随机种子 · -1随机，或0…2147483647", form.seed, enabled, signed = true) { onChange(form.copy(seed = it)) }
        ParameterHint("随机种子说明", ParameterHelp.seed)
        Text("默认线程为核心数与4的较小值；其余默认依次为0.9、50、1.0、1.05、1024、42。高级参数不用于调整播放倍速。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ParameterHint(label: String, explanation: String) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Column {
        TextButton({ expanded = !expanded }, Modifier.heightIn(min = 48.dp)) { Text(if (expanded) "收起$label" else label) }
        if (expanded) Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal object ParameterHelp {
    const val threads = "更多线程不一定更快，可能增加发热并触发降频。建议先用默认值，再根据手机实测小幅调整。"
    const val temperature = "温度控制采样随机性：较低通常更稳定，较高变化更多，也可能降低清晰度。0选择概率最高的候选。"
    const val topK = "只在概率最高的k个候选中选择；数值越小筛选越严格，0不按数量筛选。"
    const val topP = "按累计概率筛选候选；越小选择范围通常越窄，1不按累计概率裁剪。它会与Top-k共同作用。"
    const val repetition = "抑制已经出现的音频符号。过强可能损伤正常发音、节奏或完整性；不能保证消除所有重复。"
    const val frames = "这是每次模型调用的音频帧预算，不是全文长度限制。长文按自然段及段内片段生成；触顶会有限重试，仍未完整时不会发布为成功。调大上限也会增加单次耗时和资源占用。"
    const val seed = "固定seed有助于同条件复现，但不能保证跨引擎、设备或并行实现完全相同。-1在入队时抽取一次实际seed并固定段落/片段序列，恢复任务不重新抽取。"
}

@Composable
private fun NumericField(label: String, value: String, enabled: Boolean, signed: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Text else KeyboardType.Decimal))
}

@Composable
internal fun QwenParameterSummary(parameters: JSONObject) {
    fun decimal(key: String) = parameters.getDouble(key).toFloat().toString()
    Text("${parameters.getString("speaker")} · 英语 · ${parameters.getInt("threads")}线程 · seed ${parameters.getLong("seed")}", style = MaterialTheme.typography.bodySmall)
    Text("Temperature ${decimal("temperature")} · Top-k ${parameters.getInt("topK")} · Top-p ${decimal("topP")} · 重复惩罚 ${decimal("repetitionPenalty")} · 上限 ${parameters.getInt("maxAudioTokens")}帧", style = MaterialTheme.typography.bodySmall)
    Text("语气指令：${parameters.getString("instruction").ifBlank { "无" }}", style = MaterialTheme.typography.bodySmall)
}
