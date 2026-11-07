package dev.visora

import kotlin.math.min
import kotlin.math.roundToInt

/** Centers a bounded composition within the actual window, including overscan compensation. */
object DisplayViewport {
    fun size(
        width: Int,
        height: Int,
        widthPercent: Int,
        heightPercent: Int,
        preserveAspect: Boolean,
    ): Pair<Int, Int> {
        require(width > 0 && height > 0)
        require(widthPercent in 70..100 && heightPercent in 70..100)
        val availableWidth = width * widthPercent / 100f
        val availableHeight = height * heightPercent / 100f
        if (!preserveAspect)
            return availableWidth.roundToInt().coerceAtLeast(1) to
                availableHeight.roundToInt().coerceAtLeast(1)
        val scale = min(availableWidth / 1920f, availableHeight / 1080f)
        return (1920f * scale).roundToInt().coerceAtLeast(1) to
            (1080f * scale).roundToInt().coerceAtLeast(1)
    }
}
