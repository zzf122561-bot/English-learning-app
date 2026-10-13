package com.xuesui.englishapp.modelprobe.runtime

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.xuesui.englishapp.modelprobe.features.reading.BatchPhase

/** One bounded generation FGS. A timeout stops the service without joining native work;
 * the process runtime retains its sole worker until the actual native call returns. */
class SpeechGenerationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runtime: DurableSpeechRuntime
    private var wake: PowerManager.WakeLock? = null
    private var finishing = false
    private var budgetStarted = false
    private val deadline = Runnable {
        runtime.interrupt("本次后台运行预算已用完；打开App后继续未完成自然段", this)
        finishWork()
    }
    override fun onCreate() {
        super.onCreate()
        runtime = SpeechRuntimeRegistry.get(this).background
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "语音生成任务", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // An explicit new start may arrive before Android finishes destroying the old idle
        // instance. Re-attach that start rather than silently leaving its persisted work stuck.
        if (finishing) { finishing = false; budgetStarted = false }
        try {
            val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(NOTIFICATION_ID, notification(), type)
            ensureWake()
            if (!budgetStarted) {
                budgetStarted = true
                handler.postDelayed(deadline, BUDGET_MS)
            }
            val batch = intent?.getStringExtra("batch")
            if (batch != null) when (intent.action) {
                PAUSE -> runtime.control(batch, BatchPhase.PAUSED)
                RESUME -> runtime.control(batch, BatchPhase.QUEUED)
                CANCEL -> runtime.control(batch, BatchPhase.CANCELLED)
            }
            runtime.attach(this)
        } catch (failure: RuntimeException) {
            runtime.owner.report("系统未允许后台生成：${failure.message}；已保存任务可回前台继续")
            runtime.interrupt("前台服务启动失败", this); finishWork()
        }
        return START_NOT_STICKY // no background auto-restart; foreground host performs recovery.
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        runtime.interrupt("系统前台服务时限到达；下次前台打开恢复", this)
        finishWork()
    }
    internal fun updateNotification() {
        if (!finishing) runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    }
    private fun notification(): Notification {
        val state = runtime.state.value.ledger
        val batch = state.batches.firstOrNull { it.phase == BatchPhase.RUNNING }
            ?: state.batches.firstOrNull { it.phase in setOf(BatchPhase.QUEUED, BatchPhase.INTERRUPTED, BatchPhase.PAUSED, BatchPhase.FAILED) }
        val active = state.jobs.firstOrNull { it.phase.name == "RUNNING" }
        val text = if (batch == null) "正在处理模型或语音任务；可打开App查看" else
            "${state.completed(batch.id)}/${state.jobs(batch.id).size}自然段" + (active?.let { " · 当前第${it.index + 1}段" } ?: " · ${batch.reason}")
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(batch?.title?.ifBlank { "语音生成" } ?: "语音生成")
            .setContentText(text).setOnlyAlertOnce(true).setOngoing(!finishing).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_PROGRESS)
        launchPendingIntent(this)?.let { builder.setContentIntent(it) }
        if (batch != null) {
            val paused = batch.phase in setOf(BatchPhase.PAUSED, BatchPhase.FAILED)
            builder.addAction(0, if (paused) "继续生成" else "暂停生成", command(if (paused) RESUME else PAUSE, batch.id, 1))
            builder.addAction(0, "取消生成", command(CANCEL, batch.id, 2))
        } else builder.addAction(0, "打开App管理", launchPendingIntent(this))
        return builder.build()
    }
    private fun command(action: String, batch: String, code: Int): PendingIntent = PendingIntent.getForegroundService(this, code,
        Intent(this, SpeechGenerationService::class.java).setAction(action).putExtra("batch", batch), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    internal fun ensureWake() {
        if (finishing || wake?.isHeld == true) return
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:speech-generation").apply {
            setReferenceCounted(false); acquire(WAKE_TIMEOUT_MS)
        }
    }
    internal fun releaseWake() { wake?.let { if (it.isHeld) it.release() }; wake = null }
    internal fun finishWork(releaseModel: Boolean = true) {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(deadline)
        releaseWake()
        val paused = runtime.state.value.ledger.batches.any { it.phase in setOf(BatchPhase.PAUSED, BatchPhase.FAILED) }
        if (paused) {
            stopForeground(STOP_FOREGROUND_DETACH)
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
        } else stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (releaseModel) runtime.owner.releaseWhenIdle()
    }
    override fun onDestroy() {
        handler.removeCallbacks(deadline)
        wake?.let { if (it.isHeld) it.release() }; wake = null
        runtime.detach(this)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "speech-generation-v1"
        private const val NOTIFICATION_ID = 41031
        // Conservative per-service budget, not a claim to know the OS shared 6h/24h balance.
        internal const val BUDGET_MS = 350 * 60 * 1000L
        internal const val WAKE_TIMEOUT_MS = BUDGET_MS + 60_000L
        private const val PAUSE = "speech.generation.PAUSE"
        private const val RESUME = "speech.generation.RESUME"
        private const val CANCEL = "speech.generation.CANCEL"
    }
}

internal fun launchPendingIntent(context: android.content.Context): PendingIntent? = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
    PendingIntent.getActivity(context, 41030, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
