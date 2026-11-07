package dev.visora

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import dev.visora.cinematic.RuntimeEvents

/** Observes the real five-minute cycle without opening or owning a display activity. */
object BackgroundDeviceChecks {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val settings = SettingsManager(context)
        check(settings.monitoringEnabled) { "Enable supervision before the background test" }
        check(settings.diagnosticPolicy.memory == 300_000L) {
            "Use the standard five-minute memory interval"
        }
        context.startForegroundService(Intent(context, ServiceSupervisor::class.java))
        val baselineDeadline = SystemClock.elapsedRealtime() + 15_000
        var first = RuntimeEvents.snapshot().lastOrNull { it.source == "memory" }
        while (first == null && SystemClock.elapsedRealtime() < baselineDeadline) {
            SystemClock.sleep(500)
            first = RuntimeEvents.snapshot().lastOrNull { it.source == "memory" }
        }
        val baseline = checkNotNull(first) { "No initial real memory sample" }
        val deadline = SystemClock.elapsedRealtime() + 315_000
        while (SystemClock.elapsedRealtime() < deadline) {
            check(ServiceSupervisor.active) { "Supervisor stopped during observation" }
            val sample =
                RuntimeEvents.snapshot().lastOrNull {
                    it.source == "memory" && it.sequence > baseline.sequence
                }
            if (sample != null && sample.timestamp - baseline.timestamp >= 295_000) {
                return "PASS: supervisor retained a real five-minute diagnostic cycle with no test display activity; ${sample.message}"
            }
            SystemClock.sleep(1_000)
        }
        error("No second five-minute diagnostic sample")
    }
}
