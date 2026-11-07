package dev.visora.cinematic

import android.content.Context
import android.graphics.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

class GlassClockView(context: Context) : SceneLayer(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panel = RectF(1372f, 34f, 1850f, 192f)
    private val glass =
        LinearGradient(
            1372f,
            34f,
            1850f,
            192f,
            intArrayOf(0xE51F1D3C.toInt(), 0xD00B1028.toInt(), 0xE2261751.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    private val controlColors =
        intArrayOf(0xFFFF5E70.toInt(), 0xFFFFCB48.toInt(), 0xFFA7EE55.toInt())
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val timeFormat = SimpleDateFormat("hh:mm", Locale.getDefault())
    private val suffixFormat = SimpleDateFormat("a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    private val date = Date()
    private var second = -1L
    private var clock = ""
    private var suffix = ""
    private var day = ""
    private var secondsText = ""
    var weather: WeatherSnapshot? = null
    var weatherLabel = "SET WEATHER LOCATION"
    var format24Hour = false

    override fun drawScene(canvas: Canvas) {
        val now = System.currentTimeMillis()
        if (second != now / 1000) {
            second = now / 1000
            date.time = now
            val zone = TimeZone.getDefault()
            timeFormat.timeZone = zone
            suffixFormat.timeZone = zone
            dateFormat.timeZone = zone
            timeFormat.applyPattern(if (format24Hour) "HH:mm" else "hh:mm")
            clock = timeFormat.format(date)
            suffix = if (format24Hour) "" else suffixFormat.format(date)
            day = dateFormat.format(date)
            secondsText = "%02d".format(Locale.ROOT, (now / 1000) % 60)
            contentDescription = "$day $clock $suffix $secondsText"
        }
        canvas.save()
        canvas.translate(0f, (1 - reveal) * -28)
        p.shader = glass
        p.alpha = (240 * reveal).toInt()
        p.style = Paint.Style.FILL
        canvas.drawRoundRect(panel, 28f, 28f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = 0xFF8D75C9.toInt()
        p.alpha = ((90 + 35 * sin(time * .8)) * reveal).toInt()
        canvas.drawRoundRect(panel, 28f, 28f, p)
        p.style = Paint.Style.FILL
        for (i in 0..2) {
            p.color = controlColors[i]
            p.alpha = (255 * reveal).toInt()
            canvas.drawCircle(1398f + i * 22, 55f, 6.5f, p)
        }
        p.color = 0xFFDCDFED.toInt()
        p.typeface = regular
        p.textSize = 18f
        p.alpha = (235 * reveal).toInt()
        canvas.drawText(day, 1400f, 88f, p)
        p.color = Color.WHITE
        p.typeface = medium
        p.textSize = 53f
        canvas.drawText(clock, 1397f, 146f, p)
        p.typeface = regular
        p.textSize = 22f
        canvas.drawText(suffix, 1550f, 145f, p)
        p.textSize = 15f
        p.color = 0xFF8CD8FF.toInt()
        canvas.drawText(secondsText, 1610f, 145f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0xFF8872EA.toInt()
        p.alpha = (140 * reveal).toInt()
        canvas.drawLine(1399f, 169f, 1633f, 169f, p)
        p.color = 0xFF69D9FF.toInt()
        p.alpha = (255 * reveal).toInt()
        canvas.drawLine(1399f, 169f, 1399 + (now % 60000) / 60000f * 234, 169f, p)
        p.color = 0xFF71628C.toInt()
        canvas.drawLine(1660f, 65f, 1660f, 163f, p)
        p.style = Paint.Style.FILL
        val w = weather?.takeIf { it.fresh(now) }
        if (w != null) {
            drawWeather(canvas, w.condition, 1749f, 78 + sin(time * .6).toFloat() * 2)
            p.color = Color.WHITE
            p.typeface = medium
            p.textSize = 25f
            p.textAlign = Paint.Align.CENTER
            canvas.drawText("${w.celsius}°C", 1750f, 137f, p)
            p.textSize = 14f
            p.typeface = regular
            canvas.drawText(w.location, 1750f, 159f, p)
            p.textSize = 10f
            p.color = 0xFFB4B1D8.toInt()
            canvas.drawText(w.condition.uppercase(Locale.ROOT), 1750f, 178f, p)
        } else {
            p.textAlign = Paint.Align.CENTER
            p.color = 0xFF92C7F7.toInt()
            p.textSize = 13f
            canvas.drawText(weatherLabel, 1750f, 105f, p)
            p.textSize = 11f
            p.color = 0xFF9694B9.toInt()
            canvas.drawText("LIVE LOCAL CLOCK", 1750f, 130f, p)
        }
        p.textAlign = Paint.Align.LEFT
        canvas.restore()
    }

    private fun drawWeather(canvas: Canvas, condition: String, x: Float, y: Float) {
        p.color = 0xFFD4CBFF.toInt()
        p.alpha = (255 * reveal).toInt()
        p.strokeWidth = 2f
        p.style = Paint.Style.STROKE
        if (condition == "fog") {
            for (i in 0..2) canvas.drawLine(x - 23, y + i * 8, x + 23, y + i * 8, p)
        } else if (condition == "clear") {
            canvas.drawCircle(x, y, 13f, p)
            for (i in 0..7) {
                canvas.save()
                canvas.rotate(i * 45f + time * 2, x, y)
                canvas.drawLine(x, y - 18, x, y - 25, p)
                canvas.restore()
            }
        } else {
            canvas.drawCircle(x - 10, y, 13f, p)
            canvas.drawCircle(x + 5, y - 5, 18f, p)
            canvas.drawCircle(x + 21, y + 2, 11f, p)
            canvas.drawLine(x - 19, y + 13, x + 23, y + 13, p)
            if (condition == "snow") {
                for (i in 0..2) {
                    val sx = x - 16 + i * 16
                    val sy = y + 24 + sin(time + i).toFloat() * 2
                    canvas.drawLine(sx - 3, sy, sx + 3, sy, p)
                    canvas.drawLine(sx, sy - 3, sx, sy + 3, p)
                }
            }
            if (condition == "rain" || condition == "storm")
                for (i in 0..2) {
                    val drop = (time * 15 + i * 6) % 14
                    canvas.drawLine(
                        x - 13 + i * 14,
                        y + 18 + drop,
                        x - 16 + i * 14,
                        y + 24 + drop,
                        p,
                    )
                }
        }
        p.style = Paint.Style.FILL
    }
}
