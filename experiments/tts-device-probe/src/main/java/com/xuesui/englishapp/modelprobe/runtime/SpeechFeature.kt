package com.xuesui.englishapp.modelprobe.runtime

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.*
import com.xuesui.englishapp.modelprobe.features.reading.ReadingScreen

/** Formal reader. Shared across same-Activity routes; request arrival only opens a preview. */
@Composable
fun SpeechFeature(request: SpeechReadingRequest? = null, onClose: () -> Unit = {}) {
    val vm = sharedSpeechViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val reader by vm.reading.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(vm, request?.requestId) { vm.enterReading(request) }
    RuntimeLifecycle(vm, state.busy)
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val close: () -> Unit = { if (state.busy) confirmClose = true else { vm.pageStopped(); onClose() } }
    BackHandler { close() }
    val wave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { vm.selectingDocument(false); vm.export("audio", it) }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.selectingDocument(false); vm.export("report", it) }
    ReadingScreen(reader, state, vm.supportedAbi, request != null, vm, vm, close,
        onExport = { role -> if (vm.prepareExport(role)) {
            vm.selectingDocument(true)
            val id = state.selected!!.id
            if (role == "audio") wave.launch("reading-$id.wav") else report.launch("reading-$id.json")
        } }, voiceSettings = { QwenVoiceSettings(form, !state.busy, state.instructionSupported, vm::editForm) },
        advancedSettings = { QwenAdvancedSettings(form, !state.busy, vm::editForm) },
        parameterSummary = { QwenParameterSummary(it) })
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("停止生成并返回？") },
        text = { Text("已完成片段会保存。会请求当前计算停止，未执行片段不再开始；再次进入可从历史播放。") },
        confirmButton = { TextButton({ confirmClose = false; vm.pageStopped(); onClose() }) { Text("停止并返回") } },
        dismissButton = { TextButton({ confirmClose = false }) { Text("继续等待") } })
}
