package dev.visora.cinematic

import android.content.Context
import android.graphics.*
import kotlin.math.*

/** A separate animated view per feature, with no baked-in label or icon bitmap. */
class TechnologyIconLayer(context: Context, private val index: Int) : SceneLayer(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Path()
    private val gear =
        Path().apply {
            for (i in 0 until 64) {
                val angle = i * PI / 32
                val r = if (i % 8 in 2..5) 24.0 else 18.0
                val x = (cos(angle) * r).toFloat()
                val y = (sin(angle) * r).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    private val box = RectF(-40f, -40f, 40f, 40f)
    private val labels =
        arrayOf(
            arrayOf("Software", "Development"),
            arrayOf("Automation", "& Workflows"),
            arrayOf("IoT &", "Smart Systems"),
            arrayOf("Cloud", "Solutions"),
            arrayOf("Platforms", "& Products"),
            arrayOf("Secure", "& Reliable"),
        )[index]
    private val color =
        intArrayOf(
            0xFFCF72FF.toInt(),
            0xFF3FE6FF.toInt(),
            0xFF4BDAFF.toInt(),
            0xFF6F9BFF.toInt(),
            0xFFFF9F76.toInt(),
            0xFF84F2BC.toInt(),
        )[index]
    private val glow =
        RadialGradient(
            0f,
            0f,
            60f,
            intArrayOf(
                Color.argb(65, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
    private val typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    override fun drawScene(canvas: Canvas) {
        canvas.save()
        canvas.translate(
            360 + index * 240f,
            875 + (1 - reveal) * 25 + sin(time * motion * .6 + index).toFloat() * 3,
        )
        p.style = Paint.Style.FILL
        p.shader = glow
        p.alpha = (255 * reveal).toInt()
        canvas.drawCircle(0f, 0f, 60f, p)
        p.shader = null
        p.color = Color.rgb(14, 24, 53)
        p.alpha = (225 * reveal).toInt()
        canvas.drawRoundRect(box, 19f, 19f, p)
        p.color = color
        p.alpha = ((.45 + .15 * sin(time * motion + index)) * 255 * reveal).toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        canvas.drawRoundRect(box, 19f, 19f, p)
        p.alpha = (255 * reveal).toInt()
        p.strokeWidth = 2.4f
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        when (index) {
            0 -> {
                val d = sin(time * motion).toFloat() * 2
                icon.reset()
                icon.moveTo(-13 - d, -14f)
                icon.lineTo(-27 - d, 0f)
                icon.lineTo(-13 - d, 14f)
                icon.moveTo(13 + d, -14f)
                icon.lineTo(27 + d, 0f)
                icon.lineTo(13 + d, 14f)
                icon.moveTo(6f, -20f)
                icon.lineTo(-6f, 20f)
                canvas.drawPath(icon, p)
            }
            1 -> {
                canvas.save()
                canvas.rotate(time * motion * 16)
                canvas.drawPath(gear, p)
                canvas.drawCircle(0f, 0f, 8f, p)
                canvas.restore()
            }
            2 -> {
                val pulse = .88f + .12f * sin(time * motion * 1.3f)
                canvas.save()
                canvas.scale(pulse, pulse)
                canvas.drawRoundRect(-19f, -19f, 19f, 19f, 4f, 4f, p)
                canvas.drawRoundRect(-10f, -10f, 10f, 10f, 2f, 2f, p)
                for (i in -1..1) {
                    val x = i * 12f
                    canvas.drawLine(x, -26f, x, -20f, p)
                    canvas.drawLine(x, 20f, x, 26f, p)
                    canvas.drawLine(-26f, x, -20f, x, p)
                    canvas.drawLine(20f, x, 26f, x, p)
                }
                canvas.restore()
            }
            3 -> {
                icon.reset()
                icon.moveTo(-22f, 15f)
                icon.cubicTo(-38f, 5f, -28f, -11f, -14f, -7f)
                icon.cubicTo(-16f, -30f, 18f, -31f, 22f, -8f)
                icon.cubicTo(41f, -8f, 41f, 18f, 23f, 18f)
                icon.lineTo(-20f, 18f)
                canvas.drawPath(icon, p)
                for (i in 0..3) {
                    val x = -28 + (time * motion * 8 + i * 15) % 60
                    p.alpha = (80 * reveal).toInt()
                    canvas.drawCircle(x, 25 + sin(time + i).toFloat() * 3, 1.2f, p)
                }
            }
            4 -> {
                for (i in 0..2) {
                    val y = (i - 1) * 13f + sin(time * motion * .8 + i).toFloat() * 2
                    icon.reset()
                    icon.moveTo(0f, y - 11)
                    icon.lineTo(26f, y)
                    icon.lineTo(0f, y + 11)
                    icon.lineTo(-26f, y)
                    icon.close()
                    p.alpha = ((160 + i * 40) * reveal).toInt()
                    canvas.drawPath(icon, p)
                }
            }
            5 -> {
                val pulse = 1f + .04f * sin(time * motion * 1.2).toFloat()
                canvas.save()
                canvas.scale(pulse, pulse)
                icon.reset()
                icon.moveTo(0f, -26f)
                icon.quadTo(13f, -18f, 24f, -17f)
                icon.lineTo(21f, 6f)
                icon.quadTo(16f, 21f, 0f, 28f)
                icon.quadTo(-16f, 21f, -21f, 6f)
                icon.lineTo(-24f, -17f)
                icon.quadTo(-13f, -18f, 0f, -26f)
                canvas.drawPath(icon, p)
                icon.reset()
                icon.moveTo(-10f, 0f)
                icon.lineTo(-2f, 9f)
                icon.lineTo(12f, -9f)
                canvas.drawPath(icon, p)
                canvas.restore()
            }
        }
        p.style = Paint.Style.FILL
        p.alpha = (240 * reveal).toInt()
        p.color = Color.rgb(226, 230, 250)
        p.typeface = typeface
        p.textAlign = Paint.Align.CENTER
        p.textSize = 17f
        canvas.drawText(labels[0], 0f, 66f, p)
        canvas.drawText(labels[1], 0f, 87f, p)
        canvas.restore()
    }
}
