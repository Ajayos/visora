package dev.visora

import android.app.ActivityManager
import android.content.Context
import android.os.*
import org.json.JSONArray
import org.json.JSONObject

class DeviceHealthMonitor(private val context: Context) {
    fun snapshot(): JSONObject {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(
            memory,
        )
        val stat = StatFs(context.filesDir.path)
        val apps = JSONArray()
        SettingsManager(context).allowedApps.forEach { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }
                .onSuccess {
                    apps.put(JSONObject().put("package", pkg).put("version", it.versionName))
                }
        }
        return JSONObject()
            .put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("uptimeMs", SystemClock.elapsedRealtime())
            .put("supervisorRunning", ServiceSupervisor.active)
            .put("castingObservation", ServiceSupervisor.snapshot.casting)
            .put("totalMemoryBytes", memory.totalMem)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("managementServiceRunning", ManagementService.active)
            .put("availableMemoryBytes", memory.availMem)
            .put("lowMemory", memory.lowMemory)
            .put("freeStorageBytes", stat.availableBytes)
            .put("processPssKb", Debug.getPss())
            .put("networkAvailable", NetworkMonitor(context).available())
            .put("vpnPresent", NetworkMonitor(context).vpnPresent())
            .put("tailnetAddress", TailscaleMonitor(context).address() ?: JSONObject.NULL)
            .put(
                "animation",
                dev.visora.cinematic.CinematicDiagnostics.snapshot.let { d ->
                    JSONObject()
                        .put("running", d.running)
                        .put("frames", d.frames)
                        .put("averageFrameIntervalMs", d.averageIntervalMs)
                        .put("quality", d.quality)
                        .put("hardwareAccelerated", d.hardwareAccelerated)
                        .put("startupPhase", d.phase)
                        .put("ambient", d.ambient)
                        .put("layers", d.independentLayers)
                        .put("gpuFrames", d.gpuFrames)
                        .put("gpuFps", d.gpuFps)
                        .put("gpuReady", d.gpuReady)
                        .put("gpuError", d.gpuError)
                },
            )
            .put("mode", SettingsManager(context).mode.name)
            .put("apps", apps)
    }
}
