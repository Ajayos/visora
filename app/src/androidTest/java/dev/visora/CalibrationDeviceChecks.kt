package dev.visora

import android.app.Instrumentation
import android.content.Intent
import android.view.KeyEvent

/** Real remote input must change persisted dimensions and survive reopening calibration. */
object CalibrationDeviceChecks {
    fun run(test: Instrumentation): String {
        val settings = SettingsManager(test.targetContext)
        val originalWidth = settings.displayWidth
        val originalHeight = settings.displayHeight
        val originalFit = settings.displayFit
        var activity: DisplayCalibrationActivity? = null
        try {
            settings.displayWidth = 100
            settings.displayHeight = 100
            settings.displayFit = true
            fun open(): DisplayCalibrationActivity =
                test.startActivitySync(
                    Intent(test.targetContext, DisplayCalibrationActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ) as DisplayCalibrationActivity
            activity = open()
            test.waitForIdleSync()
            repeat(5) { test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT) }
            repeat(7) { test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            check(
                settings.displayWidth == 95 && settings.displayHeight == 93 && !settings.displayFit,
            )
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            test.waitForIdleSync()
            activity = open()
            test.waitForIdleSync()
            check(
                settings.displayWidth == 95 && settings.displayHeight == 93 && !settings.displayFit,
            )
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            check(
                settings.displayWidth == 100 &&
                    settings.displayHeight == 100 &&
                    settings.displayFit,
            )
            repeat(40) { test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT) }
            check(settings.displayWidth == 70) { "Remote repeat escaped lower bound" }
            return "PASS: real remote width/height adjustment, mode toggle, reopen persistence, reset, and lower bound"
        } finally {
            settings.displayWidth = originalWidth
            settings.displayHeight = originalHeight
            settings.displayFit = originalFit
            val opened = activity
            test.runOnMainSync { opened?.finish() }
        }
    }
}
