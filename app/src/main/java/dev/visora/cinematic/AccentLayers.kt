package dev.visora.cinematic

import android.content.Context
import android.graphics.*
import java.util.Random
import kotlin.math.*

class OrbitLayer(context: Context) : SceneLayer(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val oval = RectF(600f, 192f, 1320f, 370f)
    private val matrix = Matrix()
    private val shader =
        SweepGradient(
            960f,
            280f,
            intArrayOf(
                Color.TRANSPARENT,
                0xFF4A6EFF.toInt(),
                0xFFED42FF.toInt(),
                0xFF2DE7FF.toInt(),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, .28f, .48f, .7f, 1f),
        )

    override fun drawScene(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-14 + sin(time * motion * .07).toFloat() * 3, 960f, 280f)
        matrix.setRotate(time * motion * 10, 960f, 280f)
        shader.setLocalMatrix(matrix)
        p.shader = shader
        p.alpha = (reveal * 150).toInt()
        p.strokeWidth = 7f
        canvas.drawOval(oval, p)
        p.alpha = (reveal * 255).toInt()
        p.strokeWidth = 1.5f
        canvas.drawOval(oval, p)
        p.shader = null
        val a = time * motion * .65
        val x = 960 + 360 * cos(a).toFloat()
        val y = 281 + 89 * sin(a).toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.rgb(155, 234, 255)
        for (i in 4 downTo 0) {
            p.alpha = ((if (i == 0) 255 else 30) * reveal).toInt()
            canvas.drawCircle(x, y, 2f + i * 3, p)
        }
        p.style = Paint.Style.STROKE
        canvas.restore()
    }
}

class ParticleLayer(context: Context) : SceneLayer(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(561)
    private val particles =
        FloatArray(160 * 4).also { a ->
            for (i in a.indices step 4) {
                a[i] = random.nextFloat() * 1920
                a[i + 1] = random.nextFloat() * 900
                a[i + 2] = random.nextFloat() * 6.28f
                a[i + 3] = .3f + random.nextFloat()
            }
        }
    var forming = false

    override fun drawScene(canvas: Canvas) {
        val t = time * motion
        val count = if (quality == 0) 64 else 160
        for (i in 0 until count) {
            val at = i * 4
            val phase = particles[at + 2]
            var x = particles[at] + sin(t * .15 + phase).toFloat() * 25
            var y = particles[at + 1] + cos(t * .12 + phase).toFloat() * 28
            var brightness = .18f + .16f * sin(t * .5 + phase).toFloat()
            if (forming && time in 2f..6f) {
                val f = SceneTimeline.reveal(time, 2f, 4f)
                x += (730 + (i % 24) * 20 - x) * f
                y += (235 + (i % 9) * 12 - y) * f
                brightness = (1 - f) * .8f
            }
            p.color = if (i % 3 == 0) Color.rgb(252, 92, 233) else Color.rgb(79, 195, 255)
            p.alpha = (brightness * 255 * reveal).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, particles[at + 3] * 2, p)
        }
    }
}
