package dev.visora

import android.app.ActivityManager
import android.content.Context
import android.os.*
import dev.visora.cinematic.CinematicDiagnostics
import java.io.File

class CpuSampler {
    private var wall = 0L
    private var process = 0L
    private var total = 0L
    private var idle = 0L

    data class Sample(val processPercent: Double?, val systemPercent: Double?)

    fun sample(
        now: Long = SystemClock.elapsedRealtime(),
        processMs: Long = Process.getElapsedCpuTime(),
        stat: String? = runCatching {
            File("/proc/stat").bufferedReader().use { it.readLine() }
        }
            .getOrNull(),
    ): Sample {
        val processPercent =
            if (wall > 0 && now > wall)
                ((processMs - process) * 100.0 / (now - wall)).coerceAtLeast(0.0)
            else null
        wall = now
        process = processMs
        var system: Double? = null
        val fields = stat?.trim()?.split(Regex("\\s+"))
        if (fields?.firstOrNull() == "cpu" && fields.size >= 9) {
            val values = fields.drop(1).take(8).mapNotNull(String::toLongOrNull)
            if (values.size == 8) {
                val nextTotal = values.sum()
                val nextIdle = values[3] + values[4]
                if (total > 0 && nextTotal > total)
                    system =
                        (100.0 * (1.0 - (nextIdle - idle).toDouble() / (nextTotal - total)))
                            .coerceIn(0.0, 100.0)
                total = nextTotal
                idle = nextIdle
            }
        }
        return Sample(processPercent, system)
    }
}

class SystemDiagnostics(private val context: Context) {
    data class Memory(val total: Long, val available: Long, val processKb: Long, val low: Boolean)

    fun memory(): Memory {
        val m = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(m)
        return Memory(m.totalMem, m.availMem, Debug.getPss(), m.lowMemory)
    }

    fun storage(): Long = StatFs(context.filesDir.path).availableBytes

    fun health(): String {
        val d = CinematicDiagnostics.snapshot
        val sdk = Build.VERSION.SDK_INT
        val model = Build.MODEL
        val uptime = SystemClock.elapsedRealtime() / 1000
        return "$model / Android ${Build.VERSION.RELEASE} API $sdk / uptime ${uptime}s / PID ${Process.myPid()} / GPU ${"%.1f".format(java.util.Locale.ROOT,d.gpuFps)} FPS / render ${if(d.running) "active" else "paused"} / management ${if(ManagementService.active) "running" else "not running"}"
    }

    fun crash(): String? =
        File(context.filesDir, "last-crash.txt")
            .takeIf { it.isFile }
            ?.bufferedReader()
            ?.use { it.readLine()?.take(120) }
}
