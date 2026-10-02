package com.xuesui.englishapp.modelprobe.runtime

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && context.activity()?.isChangingConfigurations != true) vm.pageStopped()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (context.activity()?.isChangingConfigurations != true) vm.pageStopped()
        }
    }
    DisposableEffect(state.busy, view) {
        val before = view.keepScreenOn
        if (state.busy) view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }
    val talker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.chooseImport("talker", it) }
    val codec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.chooseImport("tokenizer", it) }
    val wave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { vm.export("audio", it) }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.export("report", it) }
    SpeechScreen(state, vm.descriptor, vm.supportedAbi, vm, onTalker = { talker.launch(arrayOf("*/*")) }, onCodec = { codec.launch(arrayOf("*/*")) },
        onExport = { role ->
            if (vm.prepareExport(role)) {
                val id = state.selected!!.id
                if (role == "audio") wave.launch("probe-$id.wav") else report.launch("probe-$id.json")
            }
        }, settings = { QwenSettings(form, !state.busy, state.instructionSupported, vm::editForm) },
        parameterSummary = { QwenParameterSummary(it) })
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}
