package dev.visora

import android.app.*
import android.content.*
import android.os.*
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action in
                setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED) &&
                SettingsManager(context).boot
        ) {
            context.startForegroundService(Intent(context, StartupController::class.java))
        }
    }
}

class StartupController : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var attempt = 0

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("startup", "VISORA startup", NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(
            1,
            Notification.Builder(this, "startup")
                .setContentTitle("VISORA")
                .setContentText("Preparing your display")
                .setSmallIcon(dev.visora.R.drawable.icon)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacksAndMessages(null)
        attempt = 0
        startForegroundService(Intent(this, ServiceSupervisor::class.java))
        // Cached visuals must appear even when the network is absent at boot.
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("startup", true),
            )
        } catch (e: Exception) {
            Log.e("VISORA", "Fire OS denied startup activity", e)
        }
        checkNetwork()
        return START_NOT_STICKY
    }

    private fun checkNetwork() {
        attempt++
        val connected = NetworkMonitor(this).available()
        Log.i("VISORA", "Startup network attempt=$attempt available=$connected")
        if (!connected && attempt < 6) {
            handler.postDelayed({ checkNetwork() }, 5_000)
            return
        }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
