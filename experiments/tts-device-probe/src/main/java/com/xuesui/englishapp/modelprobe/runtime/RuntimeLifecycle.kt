package com.xuesui.englishapp.modelprobe.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun RuntimeLifecycle(vm: ProbeViewModel, busy: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && context.activity()?.isChangingConfigurations != true) vm.hostStopped()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (context.activity()?.isChangingConfigurations != true) vm.pageStopped()
        }
    }
    DisposableEffect(busy, view) {
        val previous = view.keepScreenOn
        if (busy) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}
