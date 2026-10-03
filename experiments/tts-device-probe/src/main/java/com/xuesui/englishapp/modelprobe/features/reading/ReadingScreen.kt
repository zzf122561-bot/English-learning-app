package com.xuesui.englishapp.modelprobe.features.reading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuesui.englishapp.modelprobe.features.speech.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

@Composable
internal fun ReadingScreen(reader: ReadingUiState, state: SpeechUiState, supported: Boolean, fromArticle: Boolean,
    actions: ReadingActions, shared: SpeechActions, onClose: () -> Unit, onExport: (String) -> Unit,
    voiceSettings: @Composable () -> Unit, advancedSettings: @Composable () -> Unit,
    parameterSummary: @Composable (JSONObject) -> Unit) {
    var editing by rememberSaveable(fromArticle) { mutableStateOf(!fromArticle) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var paragraphMenu by remember { mutableStateOf(false) }
    var sentenceMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val idle = !state.busy && !reader.switching
    val paragraphs = reader.input?.paragraphs.orEmpty()
    val paragraphIndex = reader.selection.paragraph ?: 0
    val sentences = remember(paragraphs, paragraphIndex) { paragraphs.getOrNull(paragraphIndex)?.let(ReadingPlanner::sentences).orEmpty() }
    val history = state.history
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("英文朗读", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("先生成，随时重听", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClose) { Text(if (fromArticle) "返回文章" else "返回") }
            }
        }
        if (reader.pendingTitle != null || reader.switching) item {
            ReaderPanel("新的朗读请求") {
                Text(if (reader.switching) "正在等待当前任务停止，退出后切换。" else "收到：${reader.pendingTitle}。当前任务会保留已完成的音频。")
                if (!reader.switching) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(actions::switchReadingRequest) { Text("停止当前并切换") }
                    TextButton(actions::keepReadingRequest) { Text("保留当前任务") }
                }
            }
        }
        item {
            ReaderPanel(reader.title) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ReadingScope.FULL to "全文", ReadingScope.PARAGRAPH to "段落", ReadingScope.SENTENCE to "句子").forEach { (scope, label) ->
                        FilterChip(selected = reader.selection.scope == scope, onClick = { actions.chooseReadingScope(scope) }, label = { Text(label) }, enabled = idle)
                    }
                }
                if (reader.selection.scope != ReadingScope.FULL) Box {
                    OutlinedButton({ paragraphMenu = true }, enabled = idle && paragraphs.isNotEmpty()) { Text("选择段落：第${paragraphIndex + 1}段") }
                    DropdownMenu(paragraphMenu, { paragraphMenu = false }, Modifier.heightIn(max = 320.dp)) {
                        paragraphs.forEachIndexed { index, text -> DropdownMenuItem(text = { Text("第${index + 1}段 · ${text.take(60)}") },
                            onClick = { paragraphMenu = false; actions.chooseParagraph(index) }) }
                    }
                }
                if (reader.selection.scope == ReadingScope.SENTENCE) Box {
                    OutlinedButton({ sentenceMenu = true }, enabled = idle && sentences.isNotEmpty()) { Text(reader.selection.sentence?.take(80) ?: "选择句子") }
                    DropdownMenu(sentenceMenu, { sentenceMenu = false }, Modifier.heightIn(max = 320.dp)) {
                        sentences.forEachIndexed { index, text -> DropdownMenuItem(text = { Text("${index + 1}. $text") },
                            onClick = { sentenceMenu = false; actions.chooseSentence(text) }) }
                    }
                }
                if (editing) {
                    OutlinedTextField(reader.text, actions::editReadingText, Modifier.fillMaxWidth(), minLines = 4, maxLines = 9,
                        enabled = idle, label = { Text("粘贴英文；空行分段") }, supportingText = { Text("最多100000字符、256段。修改内容后重新选择范围；不会自动开始生成。") })
                } else {
                    val preview = when (reader.selection.scope) {
                        ReadingScope.FULL -> reader.text
                        ReadingScope.PARAGRAPH -> paragraphs.getOrNull(paragraphIndex).orEmpty()
                        ReadingScope.SENTENCE -> reader.selection.sentence.orEmpty()
                    }
                    Text(preview.take(6000), Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                    if (preview.length > 6000) Text("预览展示前6000字符；生成范围包含全部所选内容。", style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ editing = !editing }, enabled = idle) { Text(if (editing) "收起编辑" else "编辑 / 粘贴英文") }
                if (reader.requestError.isNotBlank()) Text(reader.requestError, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            ReaderPanel("声音与播放速度") {
                voiceSettings()
                Text("播放倍速：${String.format(Locale.ROOT, "%.2f", state.playbackSpeed)}×", fontWeight = FontWeight.Medium)
                Slider(state.playbackSpeed, shared::setPlaybackSpeed, valueRange = 0.5f..2f, steps = 5, enabled = !reader.switching)
                Text("播放时尽量保持音调，暂停后可继续；不会改动保存的原始音频。若设备不支持所选倍速会提示。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ shared.saveSettings(false) }, enabled = idle && state.parametersValid) { Text("保存全部设置") }
                    TextButton({ shared.saveSettings(true) }, enabled = idle) { Text("恢复默认") }
                }
                if (!state.parametersValid) Text(state.parameterError, color = MaterialTheme.colorScheme.error)
                TextButton({ advanced = !advanced }) { Text(if (advanced) "收起高级配置" else "高级生成配置") }
                if (advanced) advancedSettings()
            }
        }
        item {
            ReaderPanel("生成与保存") {
                if (!supported) Text("当前设备不支持本地模型，需要arm64-v8a。", color = MaterialTheme.colorScheme.error)
                if (!state.installed) {
                    Text("首次使用需从安装包准备约1.44GB模型副本，另需64MiB余量。")
                    Button({ shared.install() }, enabled = idle && supported && state.manifestReady) { Text("准备内置模型") }
                    if (!state.manifestReady) Text("安装包的模型清单缺失或不匹配，请由管理员检查打包资源。", color = MaterialTheme.colorScheme.error)
                } else Text("1.7B Q4模型已准备。长文按顺序分片段生成，每片完成即保存。")
                if (state.modelIssue.isNotBlank()) Text(state.modelIssue, color = MaterialTheme.colorScheme.error)
                Text("本地生成可能需要数分钟。相同正文和设置会优先复用保存的音频；随机seed每次重新生成。", style = MaterialTheme.typography.bodySmall)
                Button({ actions.generateReading() }, Modifier.fillMaxWidth(), enabled = idle && supported && state.installed && state.parametersValid && reader.rows.isNotEmpty() && reader.requestError.isBlank()) { Text("生成所选朗读") }
                if (advanced) TextButton({ actions.generateReading(false) }, enabled = idle && supported && state.installed && state.parametersValid && reader.rows.isNotEmpty() && reader.requestError.isBlank()) { Text("重新生成（不复用缓存）") }
                Text(state.message)
                if (state.busy) {
                    if (state.progress != null) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${state.phase.ifBlank { "处理中" }} · ${state.elapsedSeconds}秒")
                    Button(shared::cancel, enabled = !state.cancelling) { Text(if (state.cancelling) "等待任务退出…" else "停止生成") }
                }
                if (advanced && state.installed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(shared::unload, enabled = idle && state.loaded) { Text("释放模型内存") }
                        TextButton(shared::verify, enabled = idle && !state.loaded) { Text("校验模型") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(shared::clearCache, enabled = idle) { Text("清理缓存索引") }
                        TextButton({ deleting = true }, enabled = idle && !state.loaded) { Text("删除模型副本") }
                    }
                }
            }
        }
        state.selected?.let { run -> item {
            ReaderPanel("已保存的音频") {
                Text(run.report.optString("title").ifBlank { run.report.optJSONObject("reading")?.optString("title") ?: "朗读结果" }, fontWeight = FontWeight.Medium)
                Text("${runStatusTitle(run.status)} · ${run.id.take(8)}")
                if (run.status !in setOf("success", "cache")) Text("本次未完整完成；可播放已保存部分，并选择未完成范围重新生成。", color = MaterialTheme.colorScheme.error)
                if (!run.report.isNull("error")) Text(run.report.optString("error"), color = MaterialTheme.colorScheme.error)
                run.report.optJSONObject("audio")?.let { audio -> Text("${String.format(Locale.ROOT, "%.2f", audio.optDouble("durationSeconds"))}秒 · 原始音频") }
                if (run.report.has("audio")) {
                    Button({ shared.play(run.id) }, enabled = idle) { Text("播放 / 从头重播") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(actions::pausePlayback, enabled = reader.playback.phase in setOf(PlaybackPhase.PLAYING, PlaybackPhase.PREPARING)) { Text("暂停") }
                        OutlinedButton(actions::resumePlayback, enabled = reader.playback.phase == PlaybackPhase.PAUSED) { Text("继续") }
                        TextButton(shared::stopPlayback, enabled = reader.playback.phase !in setOf(PlaybackPhase.IDLE, PlaybackPhase.COMPLETED, PlaybackPhase.FAILED)) { Text("停止播放") }
                    }
                }
                Text(reader.playback.label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onExport("audio") }, enabled = idle && run.report.has("audio")) { Text("导出WAV") }
                    TextButton({ onExport("report") }, enabled = idle) { Text("导出报告") }
                }
                if (advanced) run.report.optJSONObject("requested")?.let { Text("此结果的生成配置"); parameterSummary(it) }
            }
        } }
        if (reader.rows.isNotEmpty()) item {
            Text("朗读队列 · ${reader.rows.count { it.status in setOf("success", "cache") }}/${reader.rows.size}已完成", style = MaterialTheme.typography.titleLarge)
        }
        items(reader.rows.indices.toList(), key = { "reading-part-$it" }) { index ->
            val row = reader.rows[index]
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.label, fontWeight = FontWeight.Medium)
                Text(when (row.status) { "pending" -> "待执行"; "not_started" -> "未执行"; "running" -> "正在生成"; else -> runStatusTitle(row.status) })
                if (row.preview.isNotBlank()) Text(row.preview, style = MaterialTheme.typography.bodySmall)
                if (row.hasAudio && row.runId != null) TextButton({ shared.play(row.runId) }, enabled = idle) { Text("播放此片段") }
            } }
        }
        item { Text("已保存的朗读", style = MaterialTheme.typography.titleLarge) }
        if (history.isEmpty()) item { Text("生成后会保存在这里。删除模型不会删除已保存音频。") }
        items(history, key = { "reading-history-${it.id}" }) { run ->
            OutlinedCard(onClick = { actions.selectReadingHistory(run.id) }, modifier = Modifier.fillMaxWidth(), enabled = idle) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val origin = run.report.optJSONObject("reading")
                    Text(run.report.optString("title").ifBlank { if (origin != null) "${origin.optString("title", "未完成队列")} · 片段${origin.optInt("itemIndex") + 1}" else "已保存的朗读" }, fontWeight = FontWeight.Medium)
                    Text("${runStatusTitle(run.status)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(run.report.optLong("createdAtEpochMillis")))}")
                    if (run.report.has("plannedCount")) Text("${run.report.optInt("completedCount")}/${run.report.optInt("plannedCount")}片段完成")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除模型私有副本？") },
        text = { Text("已保存音频和报告继续保留。再次生成前需要重新准备模型。") },
        confirmButton = { TextButton({ deleting = false; shared.deleteModel() }) { Text("删除副本") } },
        dismissButton = { TextButton({ deleting = false }) { Text("保留") } })
}

@Composable
private fun ReaderPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
