package com.xuesui.englishapp.modelprobe.core.runtime

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class WorkCancelled : RuntimeException("已取消；任务已退出")

/** Explicit cooperative token. A coroutine or a UI disposal never frees native memory. */
internal class WorkToken {
    private val lock = Any()
    private var hook: (() -> Unit)? = null
    val cancelled = AtomicBoolean(false)
    fun check() { if (cancelled.get()) throw WorkCancelled() }
    fun cancel() {
        val action = synchronized(lock) { cancelled.set(true); hook }
        action?.invoke()
    }
    fun attach(action: () -> Unit): Closeable {
        val already = synchronized(lock) { check(hook == null); hook = action; cancelled.get() }
        if (already) action()
        return Closeable { synchronized(lock) { if (hook === action) hook = null } }
    }
}

/** One owning worker, retained across rotation. Cleanup is queued after real native exit. */
internal class TaskRunner(private val activity: (Boolean, Boolean) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "model-probe-worker") }
    private val lock = Any()
    private var active: WorkToken? = null
    private var closing = false
    fun submit(body: (WorkToken) -> Unit): Boolean = synchronized(lock) {
        if (closing || active != null) return false
        val token = WorkToken()
        active = token
        activity(true, false)
        worker.execute {
            try { body(token) }
            finally { synchronized(lock) { active = null; activity(false, false) } }
        }
        true
    }
    fun cancel() {
        val token = synchronized(lock) {
            active?.also { activity(true, true) }
        }
        token?.cancel()
    }
    fun closeAfterExit(cleanup: () -> Unit) {
        val token = synchronized(lock) {
            if (closing) return
            closing = true
            active
        }
        token?.cancel()
        worker.execute { try { cleanup() } finally { worker.shutdown() } }
    }
}
