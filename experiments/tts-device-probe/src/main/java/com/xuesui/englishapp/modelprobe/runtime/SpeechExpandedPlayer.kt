package com.xuesui.englishapp.modelprobe.runtime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.PlaybackPhase
import java.util.Locale

internal fun playbackTime(millis: Long): String = String.format(Locale.ROOT, "%d:%02d", millis.coerceAtLeast(0) / 60_000, millis.coerceAtLeast(0) / 1000 % 60)

internal fun paragraphAvailabilityLabel(selection: PlayAvailability, index: Int, ledger: LedgerState): String {
    if (index in selection.audio) return "已生成"
    val batchIds = ledger.batches.filter { it.document == selection.target.documentId && it.settingsKey == selection.settingsKey }.map { it.id }.toSet()
    val job = ledger.jobs.lastOrNull { it.batch in batchIds && it.index == index && it.bodyKey == ParagraphAudio.body(selection.target.plan.input.paragraphs[index]) }
    return when {
        job == null || job.phase == ParagraphPhase.DELETED -> "缺失"
        job.phase == ParagraphPhase.RUNNING -> "生成中"
        job.phase == ParagraphPhase.FAILED -> "生成失败"
        ledger.batches.lastOrNull { it.id == job.batch }?.phase == BatchPhase.PAUSED -> "生成已暂停"
        ledger.batches.lastOrNull { it.id == job.batch }?.phase == BatchPhase.CANCELLED -> "缺失 · 生成已取消"
        job.phase == ParagraphPhase.PENDING || job.phase == ParagraphPhase.INTERRUPTED -> "待生成"
        else -> "缺失"
    }
}

private data class MissingParagraph(val selection: PlayAvailability, val index: Int, val epoch: Long)

/** The dialog owns only visibility and scrub preview; it never owns or releases a Player. */
@Composable
internal fun SpeechExpandedPlayer(vm: ProbeViewModel, onDismiss: () -> Unit) {
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val queue by vm.background.state.collectAsStateWithLifecycle()
    var missing by remember { mutableStateOf<MissingParagraph?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val choose: (Int) -> Unit = { index ->
        playback.selection?.let { selection ->
            if (index in selection.audio) {
                if (index in playback.sequence) vm.player.selectParagraph(index)
                else vm.player.playRange(selection, true, index)
            } else {
                vm.pausePlayback()
                missing = MissingParagraph(selection, index, vm.player.epoch)
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // A Dialog has its own window; these insets are independent of the host slot.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { ExpandedPlayerContent(playback, queue.ledger, onDismiss,
                { if (playback.desired) vm.pausePlayback() else vm.resumePlayback() },
                { value, run -> vm.player.seek(value, run) }, vm::setPlaybackSpeed,
                { vm.player.adjacent(-1) }, { vm.player.adjacent(1) }, choose, vm::stopPlayback,
                Modifier.safeDrawingPadding().widthIn(max = 720.dp).fillMaxSize()) }
        }
    }
    missing?.let { selected ->
        val selection = selected.selection
        AlertDialog(onDismissRequest = { missing = null }, title = { Text("第${selected.index + 1}段尚未生成") },
            text = { Text("确认后补齐当前播放范围的缺失音频，并从所选段开始播放。已有音频会保留；若生成任务已被你暂停，请到朗读详情继续生成。") },
            confirmButton = { TextButton({
                missing = null
                runCatching {
                    check(vm.player.epoch == selected.epoch) { "播放状态已变化，请重新选择段落" }
                    check(vm.supportedAbi && vm.state.value.installed) { "请先到语音设置准备本地模型" }
                    vm.form.value.parse().also { require(ReadingTarget.settingsKey(it) == selection.settingsKey) { "声音设置已变化，请重新选择播放范围" } }
                }.fold(onSuccess = { parameters ->
                    vm.player.playRange(selection, false, selected.index)
                    vm.background.enqueue(selection.target, parameters, selection.audio)
                }, onFailure = { error = it.message })
            }) { Text("补齐并播放") } },
            dismissButton = { TextButton({ missing = null }) { Text("保持暂停") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("暂时无法播放") },
        text = { Text(message) }, confirmButton = { TextButton({ error = null }) { Text("知道了") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedPlayerContent(
    playback: GlobalPlayback, ledger: LedgerState, onDismiss: () -> Unit, toggle: () -> Unit,
    seek: (Long, String?) -> Unit, speed: (Float) -> Unit, previous: () -> Unit, next: () -> Unit,
    choose: (Int) -> Unit, stop: () -> Unit, modifier: Modifier = Modifier,
) {
    val selection = playback.selection
    val paragraphs = selection?.target?.plan?.input?.paragraphs.orEmpty()
    val cursor = playback.sequence.indexOf(playback.paragraph)
    var dragging by remember(playback.runId, playback.paragraph) { mutableStateOf<Float?>(null) }
    val indexes = selection?.indexes.orEmpty()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("收起") }
            Text("正在播放", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(stop, enabled = playback.targetKey != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("停止") }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("speech-player-scroll"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(playback.title, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(if (playback.paragraph != null) "第${playback.paragraph + 1}段 / 共${paragraphs.size}段" else "保存的音频", color = MaterialTheme.colorScheme.primary)
                Text(playback.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val duration = playback.duration.coerceAtLeast(0)
                Slider(value = dragging ?: playback.position.coerceIn(0, duration).toFloat(), onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { seek(it.toLong(), playback.runId) }; dragging = null },
                    valueRange = 0f..maxOf(1L, duration).toFloat(), enabled = duration > 0 && playback.runId != null,
                    modifier = Modifier.semantics { contentDescription = "播放进度" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(playbackTime(dragging?.toLong() ?: playback.position), style = MaterialTheme.typography.bodySmall)
                    Text(if (duration > 0) playbackTime(duration) else "时长待载入", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(previous, enabled = cursor > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("上一段") }
                    Button(toggle, enabled = playback.targetKey != null && playback.phase != PlaybackPhase.COMPLETED, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (playback.desired) "暂停播放" else "继续播放")
                    }
                    TextButton(next, enabled = cursor >= 0 && cursor < playback.sequence.lastIndex, modifier = Modifier.heightIn(min = 48.dp)) { Text("下一段") }
                }
            }
            item {
                Text("播放速度 · ${playback.speed}×", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                        FilterChip(selected = playback.speed == value, onClick = { speed(value) }, label = { Text("${value}×") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(playback.paragraph?.let { paragraphs.getOrNull(it) } ?: "此音频未提供可对应的自然段正文。", style = MaterialTheme.typography.bodyLarge)
            }
            if (indexes.isNotEmpty()) {
                item { Text("自然段片段", style = MaterialTheme.typography.titleMedium) }
                items(indexes, key = { it }) { index ->
                    val selected = index == playback.paragraph
                    Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().clickable { choose(index) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("第${index + 1}段 · ${paragraphAvailabilityLabel(selection!!, index, ledger)}${if (selected) if (playback.desired) " · 当前播放" else " · 已暂停" else ""}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Text(paragraphs[index], maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
