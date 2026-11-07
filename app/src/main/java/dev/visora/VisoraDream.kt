package dev.visora

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class VisoraDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var label: TextView
    private val update =
        object : Runnable {
            override fun run() {
                label.text =
                    "V I S O R A\n\n" +
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                label.translationX = ((System.currentTimeMillis() / 60000) % 5 - 2) * 12f
                label.translationY = ((System.currentTimeMillis() / 120000) % 3 - 1) * 12f
                handler.postDelayed(this, 30_000)
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        label =
            TextView(this).apply {
                textSize = 36f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(125, 145, 120))
                setBackgroundColor(Color.BLACK)
            }
        setContentView(label)
        handler.post(update)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
