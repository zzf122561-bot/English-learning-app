package com.xuesui.englishapp.modelprobe.runtime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuesui.englishapp.modelprobe.features.reading.BatchPhase

/** Call once at the host Activity content root, including non-reader tabs. Never auto-plays. */
@Composable
fun SpeechRuntimeHost() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val runtime = remember(context.applicationContext) { SpeechRuntimeRegistry.get(context).background }
    val queue by runtime.state.collectAsStateWithLifecycle()
    var explained by rememberSaveable { mutableStateOf(false) }
    var explanation by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted }
    DisposableEffect(owner, runtime) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) runtime.foreground() }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) runtime.foreground()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(queue.ledger.batches, explained) {
        if (!explained && Build.VERSION.SDK_INT >= 33 && queue.ledger.batches.any { it.phase in setOf(BatchPhase.QUEUED, BatchPhase.RUNNING) } &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            explained = true; explanation = true
        }
    }
    if (explanation) AlertDialog(onDismissRequest = { explanation = false; denied = true }, title = { Text("显示语音任务通知") },
        text = { Text("允许通知后，可在后台查看生成进度并暂停、继续或取消。拒绝后任务仍可能运行，但通知栏无法提供这些生成控制；可回App管理。系统仍可能停止后台任务。") },
        confirmButton = { TextButton({ explanation = false; if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允许通知") } },
        dismissButton = { TextButton({ explanation = false; denied = true }) { Text("暂不允许") } })
    if (denied) AlertDialog(onDismissRequest = { denied = false }, title = { Text("通知未开启") },
        text = { Text("生成任务和已保存进度仍可在App内查看。生成任务的通知控制不可用，之后可在系统设置中开启通知。媒体播放通知由系统另行处理。") },
        confirmButton = { TextButton({ denied = false }) { Text("知道了") } })
}
