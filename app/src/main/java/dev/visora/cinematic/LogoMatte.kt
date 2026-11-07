package dev.visora.cinematic

import kotlin.math.max
import kotlin.math.min

/**
 * Extract substantial bright logo strokes, excluding stars and thin orbit trails in the flattened
 * source. Never applies new RGB colors: only the alpha channel is changed. Transparent custom
 * masters bypass this.
 */
object LogoMatte {
    fun extract(pixels: IntArray, width: Int, height: Int, smallWordmark: Boolean): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val stride = width + 1
        val integral = IntArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var sum = 0
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                val peak = max((c ushr 16) and 255, max((c ushr 8) and 255, c and 255))
                if (peak >= 205) sum++
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + sum
            }
        }
        val body = BooleanArray(pixels.size)
        val radius = if (smallWordmark) 2 else 3
        val minimum = if (smallWordmark) 8 else 23
        for (y in 0 until height) for (x in 0 until width) {
            val left = max(0, x - radius)
            val right = min(width, x + radius + 1)
            val top = max(0, y - radius)
            val bottom = min(height, y + radius + 1)
            val bright =
                integral[bottom * stride + right] -
                    integral[top * stride + right] -
                    integral[bottom * stride + left] + integral[top * stride + left]
            val c = pixels[y * width + x]
            val peak = max((c ushr 16) and 255, max((c ushr 8) and 255, c and 255))
            body[y * width + x] = bright >= minimum && peak >= 176
        }
        val retained = BooleanArray(pixels.size)
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        val minArea = if (smallWordmark) 22 else 160
        for (seed in body.indices) {
            if (!body[seed] || visited[seed]) continue
            var head = 0
            var tail = 1
            queue[0] = seed
            visited[seed] = true
            while (head < tail) {
                val i = queue[head++]
                val x = i % width
                val y = i / width
                fun visit(n: Int) {
                    if (body[n] && !visited[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (x > 0) visit(i - 1)
                if (x + 1 < width) visit(i + 1)
                if (y > 0) visit(i - width)
                if (y + 1 < height) visit(i + width)
            }
            if (tail >= minArea) for (i in 0 until tail) retained[queue[i]] = true
        }
        val output = IntArray(pixels.size)
        for (i in pixels.indices) {
            if (!retained[i]) continue
            val c = pixels[i]
            val peak = max((c ushr 16) and 255, max((c ushr 8) and 255, c and 255))
            val alpha = ((peak - 174) * 255 / 46).coerceIn(0, 255)
            output[i] = (alpha shl 24) or (c and 0x00FFFFFF)
        }
        return output
    }
}
