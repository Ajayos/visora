package dev.visora

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager

class DisplayManager(private val activity: Activity) {
    fun immersive() {
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun rounded(color: Int, radius: Float = 16f, stroke: Int? = null) =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius
                if (stroke != null) setStroke(2, stroke)
            }
    }
}
