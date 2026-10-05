package com.xuesui.englishapp.modelprobe.runtime

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    LaunchedEffect(vm, documentId, request) { vm.enterInline(documentId, request) }
    RuntimeLifecycle(vm, state.busy, vm::leaveInline)
    var management by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
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
    val idle = !state.busy && current && !inline.switching
    val playingThis = current && reader.playback.runId == inline.latest?.id
    val status = when {
        inline.error.isNotBlank() -> inline.error
        !vm.supportedAbi -> "本地语音需要arm64-v8a设备。"
        inline.switching -> "正在停止上一范围，完成后可生成当前范围。"
        state.busy -> "${inline.completed}/${inline.target?.plan?.items?.size ?: 0}片段 · ${state.phase.ifBlank { "处理中" }} · ${state.elapsedSeconds}秒"
        !state.installed -> "首次使用请到“语音”页准备本地模型。"
        !state.parametersValid -> "声音参数有误，请到“语音”页检查。"
        current && inline.latest != null && !inline.matchesSettings -> "正文或声音设置已变化，请重新生成；旧版仍在音频管理中。"
        playingThis && reader.playback.phase != PlaybackPhase.IDLE -> reader.playback.label
        else -> state.message
    }
    val fontScale = LocalDensity.current.fontScale
    Surface(modifier.fillMaxWidth().heightIn(max = 180.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints {
            val availableHeight = maxHeight
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Reserve the action row before measuring the heading. Large fonts drop the
                // secondary status line; full status stays available through the details dialog.
                Row(Modifier.fillMaxWidth().weight(1f, fill = false), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (current) "朗读 · ${inline.target!!.label}" else "朗读", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ details = true }, Modifier.semantics { contentDescription = "朗读详情、停止播放与音频管理" }) { Text("⋯") }
                }
                if (fontScale <= 1.3f && availableHeight >= 140.dp)
                    Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) Button(vm::cancel, Modifier.weight(1f), enabled = !state.cancelling) {
                        Text(if (state.cancelling) "等待退出" else "取消生成", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else Button(vm::generateInline, Modifier.weight(1f), enabled = idle && state.installed && vm.supportedAbi && state.parametersValid) {
                        Text(if (inline.latest == null) "生成朗读" else "重新生成", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    when {
                        playingThis && reader.playback.phase in setOf(PlaybackPhase.PLAYING, PlaybackPhase.PREPARING) ->
                            OutlinedButton(vm::pausePlayback, Modifier.weight(1f)) { Text("暂停", maxLines = 1) }
                        playingThis && reader.playback.phase == PlaybackPhase.PAUSED ->
                            OutlinedButton(vm::resumePlayback, Modifier.weight(1f), enabled = idle && inline.matchesSettings) { Text("继续", maxLines = 1) }
                        else -> OutlinedButton(vm::playInline, Modifier.weight(1f), enabled = idle && inline.matchesSettings) { Text("播放", maxLines = 1) }
                    }
                }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("朗读详情") }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(inline.target?.label ?: "当前范围无效"); Text(status) }
            if (state.message != status) item { Text(state.message) }
            if (inline.activeItem.isNotBlank() && state.busy) item { Text(inline.activeItem) }
            if (playingThis && reader.playback.phase in setOf(PlaybackPhase.PLAYING, PlaybackPhase.PREPARING, PlaybackPhase.PAUSED))
                item { OutlinedButton(vm::stopPlayback) { Text("停止播放") } }
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
                        Text(run.report.optJSONObject("retention")?.optString("label") ?: "旧版记录 / 已完成片段", style = MaterialTheme.typography.bodySmall)
                        Text("${runStatusTitle(run.status)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(run.report.optLong("createdAtEpochMillis")))}", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton({ export(run, "audio") }, enabled = !state.busy && run.report.has("audio")) { Text("导出WAV") }
                            TextButton({ export(run, "report") }, enabled = !state.busy) { Text("导出报告") }
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
