package com.aria.stats

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AriaStats(
    val cpuPercent: Float = 0f,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batPercent: Int = -1,
    val batTempC: Float = 0f,
    val batCharging: Boolean = false,
)

class StatsCollector(private val context: Context) {

    // Снимок CPU при прошлом замере
    private var lastCpuMs: Long = 0
    private var lastWallMs: Long = 0

    private var job: Job? = null

    private val _stats = MutableStateFlow(AriaStats())
    val stats: StateFlow<AriaStats> = _stats.asStateFlow()

    fun start(scope: CoroutineScope, intervalMs: Long = 1000L) {
        stop()
        // Прогреваем снимок, чтобы первый замер был осмысленным
        snapshotCpu()
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                _stats.value = collect()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun collect(): AriaStats {
        val cpu = readCpu()
        val (ramUsed, ramTotal) = readRam()
        val (batPct, batT, batCharging) = readBattery()
        return AriaStats(cpu, ramUsed, ramTotal, batPct, batT, batCharging)
    }

    private fun snapshotCpu() {
        lastCpuMs = Process.getElapsedCpuTime()
        lastWallMs = SystemClock.elapsedRealtime()
    }

    /**
     * CPU, потребляемое НАШИМ процессом, в % от одного ядра.
     *
     * 100% = одно ядро занято полностью.
     * 200% = два ядра заняты полностью (если несколько потоков).
     *
     * /proc/stat для чужих процессов Android 10+ недоступен,
     * поэтому считаем честно по своему процессу.
     */
    private fun readCpu(): Float {
        return try {
            val cpuMs = Process.getElapsedCpuTime()
            val wallMs = SystemClock.elapsedRealtime()

            val dCpu = cpuMs - lastCpuMs
            val dWall = wallMs - lastWallMs

            lastCpuMs = cpuMs
            lastWallMs = wallMs

            if (dWall <= 0L) 0f
            else (100.0 * dCpu / dWall).toFloat().coerceIn(0f, 999f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun readRam(): Pair<Float, Float> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val mb = 1024f * 1024f
            val total = mi.totalMem / mb
            val used = (mi.totalMem - mi.availMem) / mb
            used to total
        } catch (_: Exception) { 0f to 0f }
    }

    private fun readBattery(): Triple<Int, Float, Boolean> {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ) ?: return Triple(-1, 0f, false)

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) 100 * level / scale else -1

            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = tempTenths / 10f

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            Triple(pct, tempC, charging)
        } catch (_: Exception) { Triple(-1, 0f, false) }
    }
}