package com.xuesui.englishapp.modelprobe.runtime

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.modelprobe.core.storage.SavedRun
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.PlaybackPhase
import com.xuesui.englishapp.modelprobe.features.speech.runStatusTitle
import java.text.DateFormat
import java.util.Date

@Composable
internal fun sharedSpeechViewModel(): ProbeViewModel {
    val context = LocalContext.current
    val owner = requireNotNull(context.activity() as? ViewModelStoreOwner) { "语音入口需要Activity范围的ViewModelStore" }
    return viewModel(viewModelStoreOwner = owner, key = "speech-runtime") { ProbeViewModel(context.applicationContext, diagnostic = false) }
}

/** Keep this slot composed for the article's lifetime, outside recycling LazyColumn items.
 * It owns no navigation, Back handler, document editor or scrolling surface. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechInlineReadingFeature(documentId: String, request: SpeechReadingRequest, modifier: Modifier = Modifier) {
    val vm = sharedSpeechViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val inline by vm.inline.collectAsStateWithLifecycle()
    val reader by vm.reading.collectAsStateWithLifecycle()
    val queue by vm.background.state.collectAsStateWithLifecycle()
    val globalPlayer by vm.player.state.collectAsStateWithLifecycle()
    val batch = queue.ledger.batches.lastOrNull { it.document == documentId }
    val fullWave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { vm.background.exportPreparedFull(it) }
    LaunchedEffect(vm, documentId, request) { vm.enterInline(documentId, request) }
    RuntimeLifecycle(vm, state.busy, vm::leaveInline)
    var management by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val wave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) {
        vm.selectingDocument(false); vm.export("audio", it)
    }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        vm.selectingDocument(false); vm.export("report", it)
    }
    val export: (SavedRun, String) -> Unit = { run, role ->
        if (vm.prepareExport(role, run.id)) {
            vm.selectingDocument(true)
            if (role == "audio") wave.launch("reading-${run.id}.wav") else report.launch("reading-${run.id}.json")
        }
    }
    // Do not expose one frame of the previous article while LaunchedEffect consumes new input.
    val current = inline.target?.let { target -> target.documentId == documentId && target.plan.input.id == request.requestId &&
        target.plan.input.paragraphs == request.englishParagraphs && target.plan.input.selection.scope.name == request.scope.name &&
        target.plan.input.selection.paragraph == request.paragraphIndex && target.plan.input.selection.sentence == request.sentenceText } == true
    val idle = current && !inline.switching && (request.scope != SpeechReadingScope.SENTENCE || !state.busy)
    val playingThis = current && reader.playback.runId == inline.latest?.id
    val status = when {
        inline.error.isNotBlank() -> inline.error
        !vm.supportedAbi -> "本地语音需要arm64-v8a设备。"
        inline.switching -> "正在停止上一范围，完成后可生成当前范围。"
        queue.error.isNotBlank() -> queue.error
        batch != null -> "生成 ${queue.ledger.completed(batch.id)}/${queue.ledger.jobs(batch.id).size}自然段 · ${batch.phase.display()}"
        state.busy -> "${state.phase.ifBlank { "处理中" }} · ${state.elapsedSeconds}秒"
        !current -> "正在接收当前朗读范围；如有错误请查看朗读详情。"
        !state.installed -> "首次使用请到“语音”页准备本地模型。"
        !state.parametersValid -> "声音参数有误，请到“语音”页检查。"
        current && inline.latest != null && !inline.matchesSettings -> "正文或声音设置已变化，请重新生成；旧版仍在音频管理中。"
        playingThis && reader.playback.phase != PlaybackPhase.IDLE -> reader.playback.label
        else -> state.message
    }
    val fontScale = LocalDensity.current.fontScale
    val hasPlayback = globalPlayer.targetKey != null
    Surface(modifier.fillMaxWidth().heightIn(max = 180.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = hasPlayback) { expanded = true }, verticalArrangement = Arrangement.Center) {
                    Text(if (hasPlayback) globalPlayer.title else if (current) "朗读 · ${inline.target!!.label}" else "朗读",
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fontScale <= 1.3f) Text(if (hasPlayback) globalPlayer.label else "选择播放范围后开始", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (hasPlayback) IconButton({ if (globalPlayer.desired) vm.pausePlayback() else vm.resumePlayback() },
                    enabled = globalPlayer.phase != PlaybackPhase.COMPLETED,
                    modifier = Modifier.semantics { contentDescription = if (globalPlayer.desired) "暂停当前播放" else "继续当前播放" }) { Text(if (globalPlayer.desired) "Ⅱ" else "▶") }
                IconButton({ expanded = true }, enabled = hasPlayback, modifier = Modifier.semantics { contentDescription = "展开播放器" }) { Text("↗") }
            }
            if (globalPlayer.duration > 0) LinearProgressIndicator(
                progress = { (globalPlayer.position.toFloat() / globalPlayer.duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(vm::generateInline, enabled = idle && state.installed && vm.supportedAbi && state.parametersValid,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("生成", maxLines = 1) }
                if (request.scope != SpeechReadingScope.SENTENCE) SpeechReadingPlayButton(documentId, request, Modifier.weight(1f))
                else TextButton({ if (playingThis && reader.playback.phase == PlaybackPhase.PLAYING) vm.pausePlayback() else if (playingThis && reader.playback.phase == PlaybackPhase.PAUSED) vm.resumePlayback() else vm.playInline() },
                    enabled = idle && inline.matchesSettings, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (playingThis && reader.playback.phase == PlaybackPhase.PLAYING) "暂停单句" else "播放单句", maxLines = 1) }
                IconButton({ details = true }, Modifier.semantics { contentDescription = "朗读详情、生成控制与音频管理" }) { Text("⋯") }
            }
            if (fontScale <= 1.3f) Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (expanded) SpeechExpandedPlayer(vm) { expanded = false }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("朗读详情") }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(inline.target?.label ?: "当前范围无效"); Text(status) }
            if (state.message != status) item { Text(state.message) }
            if (inline.activeItem.isNotBlank() && state.busy) item { Text(inline.activeItem) }
            if (playingThis && reader.playback.phase in setOf(PlaybackPhase.PLAYING, PlaybackPhase.PREPARING, PlaybackPhase.PAUSED))
                item { OutlinedButton(vm::stopPlayback) { Text("停止播放") } }
            item {
                Text("播放：${globalPlayer.label} · ${globalPlayer.position / 1000}/${globalPlayer.duration / 1000}秒")
                Slider(globalPlayer.speed, vm::setPlaybackSpeed, valueRange = 0.5f..2f, steps = 5)
                Text("倍速 ${globalPlayer.speed}×，不重新生成音频")
                TextButton(vm::stopPlayback) { Text("停止播放") }
            }
            items(queue.ledger.batches.reversed(), key = { "task-" + it.id }) { task ->
                Column {
                    Text(task.title.ifBlank { "生成任务" })
                    Text("${queue.ledger.completed(task.id)}/${queue.ledger.jobs(task.id).size}自然段 · ${task.phase.display()}")
                    if (task.reason.isNotBlank()) Text(task.reason)
                    Text("正文和参数已冻结；设置变更只用于新任务。", style = MaterialTheme.typography.bodySmall)
                    FlowRow {
                        if (task.phase in setOf(BatchPhase.QUEUED, BatchPhase.RUNNING, BatchPhase.INTERRUPTED))
                            TextButton({ vm.background.control(task.id, BatchPhase.PAUSED) }) { Text("暂停生成") }
                        if (task.phase in setOf(BatchPhase.PAUSED, BatchPhase.FAILED))
                            TextButton({ vm.background.control(task.id, BatchPhase.QUEUED) }) { Text(if (task.phase == BatchPhase.FAILED) "重试未完成段" else "继续生成") }
                        if (task.phase !in setOf(BatchPhase.CANCELLED, BatchPhase.COMPLETE))
                            TextButton({ vm.background.control(task.id, BatchPhase.CANCELLED) }) { Text("取消生成") }
                    }
                }
            }
            if (request.scope == SpeechReadingScope.FULL && current) item {
                TextButton({ inline.target?.let { target -> vm.background.prepareFullExport(target) { fullWave.launch("reading-full.wav") } } }) { Text("导出此全文WAV") }
            }
            item { Button({ details = false; management = true }) { Text("管理已生成音频（${state.history.size}）") } }
        }
    }, confirmButton = { TextButton({ details = false }) { Text("返回文章") } })
    if (management) AlertDialog(onDismissRequest = { management = false }, title = { Text("已生成音频") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.history.isEmpty()) item { Text("还没有保存的音频。") }
                items(state.history, key = { it.id }) { run ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(run.report.optString("title").ifBlank { run.report.optJSONObject("reading")?.optString("title")?.ifBlank { null } ?: "旧版独立朗读" }, style = MaterialTheme.typography.titleSmall)
                        Text(run.report.optJSONObject("retention")?.optString("label") ?: run.report.optJSONObject("durableParagraph")?.optString("label") ?: "旧版记录 / 已完成片段", style = MaterialTheme.typography.bodySmall)
                        Text("${runStatusTitle(run.status)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(run.report.optLong("createdAtEpochMillis")))}", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton({ vm.play(run.id) }, enabled = run.report.has("audio")) { Text("播放") }
                            TextButton({ export(run, "audio") }, enabled = run.report.has("audio")) { Text("导出WAV") }
                            TextButton({ export(run, "report") }, enabled = true) { Text("导出报告") }
                            TextButton({ deleting = run.id }) { Text("删除") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }, confirmButton = { TextButton({ management = false }) { Text("返回文章") } })
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这项生成音频？") },
        text = { Text("删除此音频、报告和不再使用的片段。文章、模型和已导出的文件会保留。播放会停止，正在生成或导出的文件会等待释放后再清理。") },
        confirmButton = { TextButton({ deleting = null; vm.deleteGenerated(id) }) { Text("删除音频") } },
        dismissButton = { TextButton({ deleting = null }) { Text("保留") } }) }
}

internal fun BatchPhase.display(): String = when (this) {
    BatchPhase.QUEUED -> "排队中"
    BatchPhase.RUNNING -> "生成中"
    BatchPhase.INTERRUPTED -> "中断待恢复"
    BatchPhase.PAUSED -> "用户暂停"
    BatchPhase.FAILED -> "失败待手动重试"
    BatchPhase.CANCELLED -> "已取消"
    BatchPhase.COMPLETE -> "已完成"
}
