package com.xuesui.englishapp.modelprobe.core.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal interface Measurement : AutoCloseable { fun snapshot(): JSONObject }
internal interface MetricsProvider { fun begin(): Measurement; fun device(): JSONObject; fun nowNanos(): Long }

internal class AndroidMetrics(private val context: Context) : MetricsProvider {
    override fun nowNanos() = SystemClock.elapsedRealtimeNanos()
    override fun device() = JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        .put("sdk", Build.VERSION.SDK_INT).put("osRelease", Build.VERSION.RELEASE).put("abi", Build.SUPPORTED_ABIS.joinToString())
        .put("logicalProcessors", Runtime.getRuntime().availableProcessors())
        .put("pageSizeBytes", runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrNull() ?: JSONObject.NULL)
    override fun begin(): Measurement = AndroidMeasurement()
    private inner class AndroidMeasurement : Measurement {
        private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "probe-metrics").also { it.isDaemon = true } }
        private val lock = Any()
        private val started = nowNanos()
        private val samples = JSONArray()
        private var count = 0
        private var errors = 0
        private var peak = 0
        private var maximumGapMs = 0L
        private var lastNanos = started
        private var closed = false
        init { sample(); scheduler.scheduleWithFixedDelay({ sample() }, 200, 200, TimeUnit.MILLISECONDS) }
        private fun sample(): Unit = synchronized(lock) {
            if (closed) return
            try {
                val now = nowNanos()
                maximumGapMs = maxOf(maximumGapMs, (now - lastNanos) / 1_000_000); lastNanos = now
                val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info)
                peak = maxOf(peak, info.totalPss); count++
                val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temperature = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10.0)
                val thermal = context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                val sample = JSONObject().put("elapsedMillis", (now - started) / 1_000_000)
                    .put("pssKiB", info.totalPss).put("thermalStatus", thermal ?: JSONObject.NULL)
                    .put("batteryTemperatureC", temperature ?: JSONObject.NULL)
                if (samples.length() < 6000) samples.put(sample) else samples.put(5999, sample)
            } catch (_: Exception) { errors++ }
        }
        override fun close() {
            scheduler.shutdownNow()
            synchronized(lock) { if (!closed) { sample(); closed = true } }
        }
        override fun snapshot(): JSONObject = synchronized(lock) {
            JSONObject().put("nominalSampleIntervalMillis", 200).put("maximumObservedGapMillis", maximumGapMs)
                .put("sampleCount", count).put("sampleErrors", errors).put("samplesTruncated", count > samples.length())
                .put("sampledPeakPssKiB", if (count > 0) peak else JSONObject.NULL).put("samples", JSONArray(samples.toString()))
                .put("limitations", "PSS为定时采样峰值，非瞬时绝对峰值；温度来自电池，非CPU；热状态为Android等级")
        }
    }
}
