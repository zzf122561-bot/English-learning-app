package com.xuesui.englishapp.modelprobe.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenAdvancedSettings
import com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenVoiceSettings
import java.util.Locale

/** Settings only. Generation and audio management belong to the article slot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechSettingsFeature(modifier: Modifier = Modifier) {
    val vm = sharedSpeechViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.enterSettings() }
    RuntimeLifecycle(vm, state.busy)
    var advanced by rememberSaveable { mutableStateOf(false) }
    var deletingModel by rememberSaveable { mutableStateOf(false) }
    val idle = !state.busy
    LazyColumn(modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("语音设置", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text("声音与生成风格", style = MaterialTheme.typography.titleMedium)
            QwenVoiceSettings(form, idle, state.instructionSupported, vm::editForm)
        }
        item {
            Text("播放倍速：${String.format(Locale.ROOT, "%.2f", state.playbackSpeed)}×", style = MaterialTheme.typography.titleMedium)
            Slider(state.playbackSpeed, vm::setPlaybackSpeed, valueRange = 0.5f..2f, steps = 5, enabled = idle)
            Text("只调整播放，不改动原始音频。生成时的快慢风格可在语气指令中描述，不能保证精确倍速。", style = MaterialTheme.typography.bodySmall)
        }
        item {
            TextButton({ advanced = !advanced }) { Text(if (advanced) "收起高级配置" else "高级生成配置") }
            if (advanced) QwenAdvancedSettings(form, idle, vm::editForm)
            if (!state.parametersValid) Text(state.parameterError, color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.saveSettings(false) }, enabled = idle && state.parametersValid) { Text("保存设置") }
                TextButton({ vm.saveSettings(true) }, enabled = idle) { Text("恢复默认") }
            }
        }
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本地模型", style = MaterialTheme.typography.titleMedium)
                Text("Qwen3-TTS · 1.7B CustomVoice · Q4")
                Text(if (state.loaded) "已载入" else if (state.installed) "已安装 · 使用时载入" else "尚未准备 · 从安装包复制约1.44GB模型")
                if (!vm.supportedAbi) Text("当前设备不支持，需要arm64-v8a。", color = MaterialTheme.colorScheme.error)
                if (state.modelIssue.isNotBlank()) Text(state.modelIssue, color = MaterialTheme.colorScheme.error)
                if (!state.manifestReady && !state.installed) Text("安装包模型清单不可用，请由管理员检查。", color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.installed) Button({ vm.install() }, enabled = idle && vm.supportedAbi && state.manifestReady) { Text("准备内置模型") }
                    if (state.installed) {
                        TextButton(vm::verify, enabled = idle && !state.loaded) { Text("完整校验") }
                        TextButton(vm::unload, enabled = idle && state.loaded) { Text("释放模型内存") }
                        TextButton({ deletingModel = true }, enabled = idle && !state.loaded) { Text("删除模型副本") }
                    }
                }
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                if (state.busy) {
                    state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(vm::cancel, enabled = !state.cancelling) { Text(if (state.cancelling) "等待退出" else "停止当前操作") }
                }
            }
        }
    }
    if (deletingModel) AlertDialog(onDismissRequest = { deletingModel = false }, title = { Text("删除模型私有副本？") },
        text = { Text("已生成音频会保留。再次生成前需要重新准备模型。") },
        confirmButton = { TextButton({ deletingModel = false; vm.deleteModel() }) { Text("删除副本") } },
        dismissButton = { TextButton({ deletingModel = false }) { Text("保留") } })
}
