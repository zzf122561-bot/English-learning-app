package com.xuesui.englishapp.modelprobe.runtime

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenSettings
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenParameterSummary
import com.xuesui.englishapp.modelprobe.features.speech.SpeechScreen

/** Host owns the Activity/assets/package; only this composition registers a concrete task. */
@Composable
fun ModelProbeFeature() {
    val context = LocalContext.current
    val vm: ProbeViewModel = viewModel { ProbeViewModel(context.applicationContext) }
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    RuntimeLifecycle(vm, state.busy)
    val talker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.selectingDocument(false); vm.chooseImport("talker", it) }
    val codec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.selectingDocument(false); vm.chooseImport("tokenizer", it) }
    val wave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { vm.selectingDocument(false); vm.export("audio", it) }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.selectingDocument(false); vm.export("report", it) }
    SpeechScreen(state, vm.descriptor, vm.supportedAbi, vm, onTalker = { vm.selectingDocument(true); talker.launch(arrayOf("*/*")) }, onCodec = { vm.selectingDocument(true); codec.launch(arrayOf("*/*")) },
        onExport = { role ->
            if (vm.prepareExport(role)) {
                vm.selectingDocument(true)
                val id = state.selected!!.id
                if (role == "audio") wave.launch("probe-$id.wav") else report.launch("probe-$id.json")
            }
        }, settings = { QwenSettings(form, !state.busy, state.instructionSupported, vm::editForm) },
        parameterSummary = { QwenParameterSummary(it) })
}

internal fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}
