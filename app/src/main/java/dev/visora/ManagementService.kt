package dev.visora

import android.app.*
import android.content.*
import android.os.*
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class ManagementService : Service() {
    companion object {
        @Volatile
        var active = false
            private set
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var failures = 0
    private var running = false

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            active = true
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    "management",
                    "VISORA device management",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val pi =
                PendingIntent.getActivity(
                    this,
                    2,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            startForeground(
                2,
                Notification.Builder(this, "management")
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle("VISORA device management")
                    .setContentText("Cached playback stays available offline")
                    .setContentIntent(pi)
                    .build(),
            )
            executor.execute { sync() }
        }
        return START_STICKY
    }

    private fun sync() {
        if (SettingsManager(this).token() == null) {
            stopSelf()
            return
        }
        try {
            RemoteManagementClient(this).poll((application as VisoraApp).engine, ::execute)
            dev.visora.cinematic.BackendConnection.reached()
            dev.visora.cinematic.RuntimeEvents.publish(
                dev.visora.cinematic.EventLevel.OK,
                "management",
                "Authenticated configuration sync completed",
            )
            failures = 0
        } catch (e: Exception) {
            dev.visora.cinematic.BackendConnection.failed()
            dev.visora.cinematic.RuntimeEvents.publish(
                dev.visora.cinematic.EventLevel.WARNING,
                "management",
                "Sync unavailable; retry backoff active (${e.javaClass.simpleName})",
            )
            failures = (failures + 1).coerceAtMost(6)
            Log.w("VISORA", "Management sync failed: ${e.javaClass.simpleName}")
        }
        if (!executor.isShutdown)
            executor.schedule(
                { sync() },
                (if (failures == 0) SettingsManager(this).diagnosticPolicy.sync / 1000
                else (15L * (1L shl failures)).coerceAtMost(300)) + (0..5).random(),
                TimeUnit.SECONDS,
            )
    }

    private fun execute(type: String, args: JSONObject): String {
        dev.visora.cinematic.RuntimeEvents.publish(
            dev.visora.cinematic.EventLevel.RUNNING,
            "management",
            "Authorized command received: $type",
        )
        when (type) {
            "set_mode" -> {
                val mode = DisplayMode.valueOf(args.getString("mode"))
                SettingsManager(this).mode = mode
                if (mode == DisplayMode.CASTING) {
                    if (!CastingController(this).launch()) return "failed:AirScreen_unavailable"
                } else
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("refresh", true),
                    )
                return "mode_requested"
            }
            "restart" -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                return "activity_restart_requested"
            }
            "launch_app" ->
                return if (CastingController(this).launch(args.getString("package")))
                    "launch_requested"
                else "rejected:unavailable_or_not_allowlisted"
            "stage_update" -> {
                val temp = File(filesDir, "candidate.apk")
                try {
                    RemoteManagementClient(this)
                        .download(args.getString("url"), temp, 64L * 1024 * 1024)
                    UpdateManager(this).verifyAndStage(temp, args.getString("sha256"))
                } finally {
                    temp.delete()
                }
                return "staged:local_install_confirmation_required"
            }
            else -> error("Unsupported command")
        }
    }

    override fun onDestroy() {
        active = false
        executor.shutdownNow()
        super.onDestroy()
    }
}
