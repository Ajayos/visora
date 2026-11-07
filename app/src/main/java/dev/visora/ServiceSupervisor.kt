package dev.visora

import android.app.*
import android.content.Intent
import android.os.IBinder
import dev.visora.cinematic.*

/**
 * The sole diagnostics owner. Activity recreation/idle does not create or stop a second scheduler.
 */
class ServiceSupervisor : Service() {
    companion object {
        @Volatile
        var active = false
            private set

        @Volatile
        var snapshot = DiagnosticSnapshot()
            private set
    }

    private var diagnostics: DiagnosticsController? = null
    private var background: BackgroundServices? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(
            NotificationChannel(
                "supervision",
                "VISORA service supervision",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val pending =
            PendingIntent.getActivity(
                this,
                3,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        startForeground(
            3,
            Notification.Builder(this, "supervision")
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("VISORA monitoring")
                .setContentText("Background service checks; five-minute diagnostics")
                .setContentIntent(pending)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SettingsManager(this).monitoringEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (diagnostics == null) {
            active = true
            background = BackgroundServices(this)
            diagnostics =
                DiagnosticsController(this, { snapshot = it }, background).also { it.start() }
            RuntimeEvents.publish(
                EventLevel.RUNNING,
                "supervisor",
                "Central background supervisor started; no external app activities",
            )
        }
        val s = SettingsManager(this)
        if (s.remoteEnabled && s.token() != null)
            startForegroundService(Intent(this, ManagementService::class.java))
        return START_STICKY
    }

    override fun onDestroy() {
        active = false
        diagnostics?.close()
        diagnostics = null
        background?.close()
        background = null
        super.onDestroy()
    }
}
