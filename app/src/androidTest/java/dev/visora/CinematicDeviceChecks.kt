package dev.visora

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import dev.visora.cinematic.*

object CinematicDeviceChecks {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        for (id in listOf("aos", "deluper")) {
            val logo = LogoRepository(context).load(id)
            val pixels = IntArray(logo.width * logo.height)
            logo.getPixels(pixels, 0, logo.width, 0, 0, logo.width, logo.height)
            check(pixels.count { Color.alpha(it) == 0 } > pixels.size / 8) {
                "$id logo background not isolated"
            }
            check(pixels.count { Color.alpha(it) > 200 } > pixels.size / 12) {
                "$id supplied logo missing"
            }
            logo.recycle()
        }
        var paintedGlyphs = 0
        instrumentation.runOnMainSync {
            val terminal =
                LiveTerminalView(context, AnimationConfig()).apply {
                    layout(0, 0, 1920, 1080)
                    reveal = 1f
                    append(
                        RuntimeEvent(
                            1,
                            System.currentTimeMillis(),
                            EventLevel.OK,
                            "test",
                            "ACTUAL DEVICE TERMINAL RENDER TEST",
                        ),
                    )
                }
            for (i in 0..30) terminal.frame(6 + i * .1f, .1f, 1, 1f)
            val image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            terminal.draw(Canvas(image))
            for (y in 503 until 547) for (x in 104 until 900) {
                val c = image.getPixel(x, y)
                if (Color.green(c) > 130 && Color.alpha(c) > 100) paintedGlyphs++
            }
            image.recycle()
        }
        check(paintedGlyphs > 30) { "Live terminal glyphs not rendered" }
        val activity =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        val deadline = SystemClock.elapsedRealtime() + 35_000
        while (
            SystemClock.elapsedRealtime() < deadline &&
                (!CinematicDiagnostics.snapshot.ambient ||
                    CinematicDiagnostics.snapshot.frames < 200)
        ) Thread.sleep(250)
        Thread.sleep(2500)
        val first = CinematicDiagnostics.snapshot
        check(first.ambient && first.frames >= 200) {
            "Cinematic scene never became active: $first"
        }
        check(first.hardwareAccelerated && first.independentLayers >= 12)
        check(first.gpuReady && first.gpuError == 0 && first.gpuFrames > 100) {
            "GLES rendering not verified: $first"
        }
        val before = RuntimeEvents.snapshot().map { it.sequence }.toSet()
        instrumentation.runOnMainSync {
            val root = activity.window.decorView
            fun visibleButton(view: android.view.View): Boolean {
                if (view is android.widget.Button && view.visibility == android.view.View.VISIBLE)
                    return true
                if (view is android.view.ViewGroup)
                    for (i in 0 until view.childCount) if (visibleButton(view.getChildAt(i)))
                        return true
                return false
            }
            check(!visibleButton(root)) { "Permanent buttons remain on the main screen" }
            fun findScene(view: android.view.View): CinematicScene? {
                if (view is CinematicScene) return view
                if (view is android.view.ViewGroup)
                    for (i in 0 until view.childCount) findScene(view.getChildAt(i))?.let {
                        return it
                    }
                return null
            }
            val scene = requireNotNull(findScene(root))
            scene.configure(scene.config.copy(enabled = false), 17)
            root.dispatchKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                ),
            )
            root.dispatchKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                ),
            )
        }
        Thread.sleep(1400)
        check(!CinematicDiagnostics.snapshot.ambient && CinematicDiagnostics.snapshot.seconds < 5) {
            "Manual replay did not animate with automatic intro disabled"
        }
        check(RuntimeEvents.snapshot().any { it.sequence in before }) {
            "Replay cleared actual history"
        }
        val after = RuntimeEvents.snapshot()
        check(
            after.count { it.source == "startup" } ==
                before.count { seq -> after.any { it.sequence == seq && it.source == "startup" } },
        ) {
            "Replay reran startup tasks"
        }
        check(after.any { it.message.startsWith("Visual intro replay requested") })
        val beforeDiagnostic =
            RuntimeEvents.snapshot().lastOrNull { it.source == "memory" }?.sequence ?: 0L
        instrumentation.runOnMainSync { activity.onUserInteraction() }
        Thread.sleep(301_000)
        val idleDeadline = SystemClock.elapsedRealtime() + 10_000L
        while (
            CinematicDiagnostics.snapshot.running && SystemClock.elapsedRealtime() < idleDeadline
        ) Thread.sleep(250)
        check(
            RuntimeEvents.snapshot().any {
                it.source == "memory" && it.sequence > beforeDiagnostic
            },
        ) {
            "Periodic diagnostics stopped after startup"
        }
        check(!CinematicDiagnostics.snapshot.running) {
            "Graphics did not pause after five minutes inactivity: ${CinematicDiagnostics.snapshot}; events=${RuntimeEvents.snapshot().takeLast(8)}"
        }
        val idleFrames = CinematicDiagnostics.snapshot.frames
        Thread.sleep(1200)
        check(CinematicDiagnostics.snapshot.frames == idleFrames) {
            "Graphics continued behind idle screen"
        }
        instrumentation.runOnMainSync {
            check(activity.hasWindowFocus()) {
                "TEST INTERRUPTED: another window took focus during the timed remote test"
            }
            activity.dispatchKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                ),
            )
            activity.dispatchKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                ),
            )
        }
        val wakeDeadline = SystemClock.elapsedRealtime() + 5000L
        while (
            (!CinematicDiagnostics.snapshot.running ||
                CinematicDiagnostics.snapshot.frames <= idleFrames) &&
                SystemClock.elapsedRealtime() < wakeDeadline
        ) Thread.sleep(100)
        check(
            CinematicDiagnostics.snapshot.running &&
                CinematicDiagnostics.snapshot.frames > idleFrames,
        ) {
            "Remote did not restore graphics: ${CinematicDiagnostics.snapshot}"
        }
        instrumentation.runOnMainSync {
            activity.startActivity(Intent(context, SettingsActivity::class.java))
        }
        Thread.sleep(1500)
        val paused = CinematicDiagnostics.snapshot
        check(!paused.running) { "Animation did not pause behind settings" }
        check(ServiceSupervisor.active) { "Central supervisor stopped behind Settings" }
        val diagnosticSequence =
            RuntimeEvents.snapshot().lastOrNull { it.source == "memory" }?.sequence ?: 0L
        val frameCount = paused.frames
        val settings = SettingsManager(context)
        val originalPolicy = settings.diagnosticPolicy
        try {
            settings.diagnosticPolicy = originalPolicy.copy(memory = 10_000)
            Thread.sleep(13_000)
            check(CinematicDiagnostics.snapshot.frames == frameCount) {
                "Frames continued while display was hidden"
            }
            check(
                RuntimeEvents.snapshot().any {
                    it.source == "memory" && it.sequence > diagnosticSequence
                },
            ) {
                "Background diagnostics stopped behind Settings"
            }
        } finally {
            settings.diagnosticPolicy = originalPolicy
        }
        return "PASS: no main-screen buttons, central supervisor continues behind Settings, five-minute actual diagnostics, idle graphics pause and remote wake, supplied logo alpha extraction, real terminal glyph rendering, GPU scene/layers, ambient readiness, visual-only replay preserving startup/history, lifecycle pause. Measured visible frame interval=${first.averageIntervalMs}ms, frames=${first.frames}, quality=${first.quality}, GPU FPS=${first.gpuFps}, GPU error=${first.gpuError}. Receiver observation: ${ServiceSupervisor.snapshot.casting}."
    }
}
