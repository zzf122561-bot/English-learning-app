package com.xuesui.englishapp.modelprobe.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuesui.englishapp.modelprobe.features.reading.*
import com.xuesui.englishapp.modelprobe.features.speech.PlaybackPhase

internal fun readingTarget(documentId: String, request: SpeechReadingRequest): ReadingTarget = ReadingTarget(documentId,
    ReadingPlanner.create(ReadingInput(request.requestId, request.title, request.englishParagraphs.toList(),
        ReadingSelection(ReadingScope.valueOf(request.scope.name), request.paragraphIndex, request.sentenceText))))

/** Public direct-play entry. Missing audio is always a user-confirmed inference operation. */
@Composable
fun SpeechReadingPlayButton(documentId: String, request: SpeechReadingRequest, modifier: Modifier = Modifier) {
    val vm = sharedSpeechViewModel()
    val playing by vm.player.state.collectAsStateWithLifecycle()
    val targetResult = remember(documentId, request) { runCatching {
        require(request.scope != SpeechReadingScope.SENTENCE) { "直接播放按钮只接受全文或自然段" }
        readingTarget(documentId, request)
    } }
    val target = targetResult.getOrNull()
    var confirmation by remember(documentId, request) { mutableStateOf<PlayAvailability?>(null) }
    var error by remember(documentId, request) { mutableStateOf<String?>(null) }
    var checking by remember(documentId, request) { mutableStateOf(false) }
    var active by remember(documentId, request) { mutableStateOf(true) }
    DisposableEffect(documentId, request) { active = true; onDispose { active = false } }
    val selected = target != null && playing.targetKey == ManagedSpeechPlayback.key(target) &&
        playing.phase !in setOf(PlaybackPhase.IDLE, PlaybackPhase.COMPLETED, PlaybackPhase.FAILED)
    val scope = if (request.scope == SpeechReadingScope.FULL) "全文" else "本段"
    val label = when { checking -> "读取音频"; selected && playing.desired -> "暂停$scope"; selected -> "继续$scope"; else -> "播放$scope" }
    TextButton(onClick = {
        if (selected) { if (playing.desired) vm.pausePlayback() else vm.resumePlayback() }
        else if (target == null) error = targetResult.exceptionOrNull()?.message
        else {
            checking = true
            val ticket = vm.player.newSelection()
            vm.background.inspect(target) { result ->
                if (active) {
                    checking = false
                    if (ticket == vm.player.epoch)
                        result.fold(onSuccess = { if (it.missing == 0) vm.player.playRange(it, false) else confirmation = it }, onFailure = { error = it.message })
                }
            }
        }
    }, modifier = modifier, enabled = !checking) { Text(label) }
    confirmation?.let { availability ->
        val full = request.scope == SpeechReadingScope.FULL
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(if (full) "补齐全文音频？" else "生成本段音频？") },
            text = { Text(if (full) "已有 ${availability.audio.size} 段，缺少 ${availability.missing} 段。补齐并播放会在缺段处等待；仅播放已有内容会跳过缺段，并显示实际段号。"
                else "本段尚无与当前正文、声音设置匹配的音频。生成会保存为任务，完整保存后播放；你可以分别暂停生成或播放。") },
            confirmButton = { TextButton({
                confirmation = null
                runCatching { vm.form.value.parse().also {
                    check(vm.supportedAbi && vm.state.value.installed) { "请先到语音设置准备本地模型" }
                    require(ReadingTarget.settingsKey(it) == availability.settingsKey) { "设置已变化，请重新点击播放确认" }
                } }.fold(onSuccess = { parameters ->
                    // Set the playback intention now. Pausing or changing target while enqueue
                    // runs cannot later be undone by a completion callback.
                    vm.player.playRange(availability, false)
                    vm.background.enqueue(availability.target, parameters, availability.audio)
                }, onFailure = { error = it.message })
            }) { Text(if (full) "补齐并播放" else "生成并播放") } },
            dismissButton = { Column {
                if (full) TextButton({ confirmation = null; vm.player.playRange(availability, true) }, enabled = availability.audio.isNotEmpty()) { Text("仅播放已有内容") }
                TextButton({ confirmation = null }) { Text("取消") }
            } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("暂时无法播放") },
        text = { Text(message) }, confirmButton = { TextButton({ error = null }) { Text("知道了") } }) }
}
