package com.xuesui.englishapp.modelprobe.engines.qwen3tts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
        NumericField("CPU线程 · 1…${Runtime.getRuntime().availableProcessors()}", form.threads, enabled) { onChange(form.copy(threads = it)) }
        NumericField("Temperature · 0…2（0为greedy）", form.temperature, enabled) { onChange(form.copy(temperature = it)) }
        NumericField("Top-k · 0…2048（0关闭）", form.topK, enabled) { onChange(form.copy(topK = it)) }
        NumericField("Top-p · 大于0且不超过1", form.topP, enabled) { onChange(form.copy(topP = it)) }
        NumericField("Repetition penalty · 1…2", form.repetition, enabled) { onChange(form.copy(repetition = it)) }
        NumericField("最大音频帧 · 1…4096", form.maxTokens, enabled) { onChange(form.copy(maxTokens = it)) }
        NumericField("Seed · -1随机，或0…2147483647", form.seed, enabled, signed = true) { onChange(form.copy(seed = it)) }
        Text("触及最大音频帧会标记可能截断。逐句模式依次使用seed、seed+1…；随机模式记录每次实际seed。GPU/NPU及合成端语速尚不支持。", style = MaterialTheme.typography.bodySmall)
    }
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
