package dev.visora

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.io.File

class VisoraApp : Application() {
    val engine by lazy { PromotionEngine(this) }

    override fun onCreate() {
        super.onCreate()
        SettingsManager(this).initializeWeatherLocation(this)
        dev.visora.cinematic.RuntimeEvents.publish(
            dev.visora.cinematic.EventLevel.RUNNING,
            "lifecycle",
            "VISORA application process created",
        )
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, error ->
            dev.visora.cinematic.RuntimeEvents.publish(
                dev.visora.cinematic.EventLevel.ERROR,
                "crash",
                "Uncaught ${error.javaClass.simpleName}; bounded recovery scheduled when permitted",
            )
            Log.e("VISORA", "Uncaught failure; bounded restart", error)
            val prefs = getSharedPreferences("recovery", MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong("last", 0)
            val count = if (now - last < 300_000) prefs.getInt("count", 0) + 1 else 1
            prefs.edit().putInt("count", count).putLong("last", now).commit()
            runCatching {
                File(filesDir, "last-crash.txt")
                    .writeText(
                        "$now ${error.javaClass.name}\n${error.stackTrace.take(12).joinToString("\n")}",
                    )
            }
            if (count <= 3) {
                val pending =
                    PendingIntent.getActivity(
                        this,
                        99,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                (getSystemService(ALARM_SERVICE) as AlarmManager).set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + count * 10_000,
                    pending,
                )
            }
            previous?.uncaughtException(t, error)
        }
    }
}
