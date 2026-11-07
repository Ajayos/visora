package dev.visora

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View

/**
 * Remote-operated edge calibration using measured window dimensions, without changing OS
 * resolution.
 */
class DisplayCalibrationActivity : Activity() {
    private val settings by lazy { SettingsManager(this) }
    private lateinit var guide: View

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DisplayManager(this).immersive()
        guide =
            object : View(this) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                    override fun onDraw(canvas: Canvas) {
                        canvas.drawColor(Color.BLACK)
                        val size =
                            DisplayViewport.size(
                                width,
                                height,
                                settings.displayWidth,
                                settings.displayHeight,
                                settings.displayFit,
                            )
                        val left = (width - size.first) / 2f
                        val top = (height - size.second) / 2f
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        paint.color = 0xFF98DAFF.toInt()
                        canvas.drawRect(
                            left + 3,
                            top + 3,
                            left + size.first - 3,
                            top + size.second - 3,
                            paint,
                        )
                        paint.strokeWidth = 1f
                        paint.color = 0xFF334057.toInt()
                        for (i in 1..3) {
                            val x = left + size.first * i / 4f
                            val y = top + size.second * i / 4f
                            canvas.drawLine(x, top, x, top + size.second, paint)
                            canvas.drawLine(left, y, left + size.first, y, paint)
                        }
                        canvas.save()
                        val scale = minOf(width / 1920f, height / 1080f)
                        canvas.translate(width / 2f, height / 2f)
                        canvas.scale(scale, scale)
                        paint.style = Paint.Style.FILL
                        paint.color = 0xF00A1020.toInt()
                        canvas.drawRoundRect(-620f, -220f, 620f, 220f, 24f, 24f, paint)
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = Color.WHITE
                        paint.textSize = 38f
                        canvas.drawText("VISORA / Screen calibration", 0f, -140f, paint)
                        paint.textSize = 29f
                        canvas.drawText(
                            "Width ${settings.displayWidth}%   ·   Height ${settings.displayHeight}%",
                            0f,
                            -72f,
                            paint,
                        )
                        paint.textSize = 24f
                        canvas.drawText(
                            "Window ${width} × ${height}   /   Display ${size.first} × ${size.second}",
                            0f,
                            -24f,
                            paint,
                        )
                        canvas.drawText(
                            if (settings.displayFit) "Mode: preserve 16:9"
                            else "Mode: independent width and height",
                            0f,
                            20f,
                            paint,
                        )
                        paint.textSize = 22f
                        paint.color = 0xFFB9D5E8.toInt()
                        canvas.drawText(
                            "← → width     ↑ ↓ height     Select: change mode",
                            0f,
                            82f,
                            paint,
                        )
                        canvas.drawText("Menu: reset     Back: save and return", 0f, 128f, paint)
                        canvas.drawText(
                            "Adjust until all four blue edges are visible on your TV.",
                            0f,
                            174f,
                            paint,
                        )
                        canvas.restore()
                    }
                }
                .apply {
                    contentDescription =
                        "Screen edge calibration; use remote arrows to adjust width and height"
                }
        setContentView(guide)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT ->
                settings.displayWidth = (settings.displayWidth - 1).coerceAtLeast(70)
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                settings.displayWidth = (settings.displayWidth + 1).coerceAtMost(100)
            KeyEvent.KEYCODE_DPAD_UP ->
                settings.displayHeight = (settings.displayHeight + 1).coerceAtMost(100)
            KeyEvent.KEYCODE_DPAD_DOWN ->
                settings.displayHeight = (settings.displayHeight - 1).coerceAtLeast(70)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER ->
                if (event.repeatCount == 0) settings.displayFit = !settings.displayFit
            KeyEvent.KEYCODE_MENU -> {
                settings.displayWidth = 100
                settings.displayHeight = 100
                settings.displayFit = true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        guide.invalidate()
        return true
    }
}
