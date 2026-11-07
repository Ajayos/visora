package dev.visora.cinematic

import android.content.Context
import android.graphics.*
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * Continuous real-event console. A quiet event bus produces only an animated listening indicator.
 */
class LiveTerminalView(context: Context, private val config: AnimationConfig) :
    SceneLayer(context) {
    private data class Line(val text: String, val level: EventLevel)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panel = RectF(72f, 442f, 1050f, 810f)
    private val header = RectF(73f, 443f, 1049f, 486f)
    private val bg =
        LinearGradient(
            72f,
            442f,
            1050f,
            810f,
            intArrayOf(0xF2191531.toInt(), 0xF7040818.toInt(), 0xF10B102B.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    private val chrome =
        LinearGradient(
            72f,
            442f,
            1050f,
            486f,
            intArrayOf(0xFF303455.toInt(), 0xFF292340.toInt(), 0xFF573054.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    private val border =
        LinearGradient(
            72f,
            442f,
            1050f,
            810f,
            intArrayOf(0xFFCCA3FF.toInt(), 0xFF6680DD.toInt(), 0xFF63DAF9.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    private val controls = intArrayOf(0xFFFF5D70.toInt(), 0xFFFFC64C.toInt(), 0xFFB6F04E.toInt())
    private val mono = Typeface.create("monospace", Typeface.NORMAL)
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val queue = ArrayDeque<Line>()
    private val lines = ArrayDeque<Line>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val date = Date()
    private var typing: Line? = null
    private var chars = 0f
    private var visibleText = ""
    private var lastSequence = 0L
    private var progress = 0
    private var smoothProgress = 0f
    private var scroll = 0f
    private var textSize = 19f
    var phase = StartupPhase.IDLE
    var startup = true
    var typingCps = config.typingCps
    val isDrained
        get() = queue.isEmpty() && typing == null

    var lastMessage = "Listening for application events"
        private set

    fun fontSize(size: Int) {
        textSize = size.toFloat()
    }

    fun taskProgress(progress: Int, phase: StartupPhase) {
        this.progress = progress
        this.phase = phase
    }

    fun append(event: RuntimeEvent) {
        if (event.sequence <= lastSequence) return
        lastSequence = event.sequence
        lastMessage = event.message
        date.time = event.timestamp
        val tag =
            when (event.level) {
                EventLevel.OK -> "OK  "
                EventLevel.WARNING -> "WARN"
                EventLevel.ERROR -> "ERR "
                EventLevel.RUNNING -> "INFO"
            }
        val text = "[${clock.format(date)}] $tag ${event.message}"
        p.typeface = mono
        p.textSize = textSize
        var remaining = text
        while (remaining.isNotEmpty()) {
            var count = p.breakText(remaining, true, 906f, null).coerceAtLeast(1)
            if (count < remaining.length) {
                val space = remaining.lastIndexOf(' ', count)
                if (space > 24) count = space
            }
            queue.add(Line(remaining.take(count), event.level))
            remaining = remaining.drop(count).trimStart()
        }
        while (queue.size > 64) queue.removeFirst()
        invalidate()
    }

    override fun frame(seconds: Float, delta: Float, quality: Int, motion: Float) {
        if (reveal > 0f) {
            if (typing == null && queue.isNotEmpty()) {
                typing = queue.removeFirst()
                chars = 0f
                visibleText = ""
            }
            typing?.let { line ->
                chars += delta * typingCps
                val n = chars.toInt().coerceAtMost(line.text.length)
                if (n != visibleText.length) visibleText = line.text.substring(0, n)
                if (n == line.text.length) {
                    lines.add(line)
                    while (lines.size > 120) lines.removeFirst()
                    typing = null
                    visibleText = ""
                    scroll = textSize + 7f
                }
            }
        }
        scroll = max(0f, scroll - delta * 110)
        smoothProgress = min(progress.toFloat(), smoothProgress + delta * 90)
        super.frame(seconds, delta, quality, motion)
    }

    override fun drawScene(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, (1 - reveal) * 18 + sin(time * motion * .35).toFloat() * 1.4f)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.alpha = (255 * reveal).toInt()
        p.shader = bg
        canvas.drawRoundRect(panel, 22f, 22f, p)
        p.shader = chrome
        canvas.drawRoundRect(header, 20f, 20f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.shader = border
        p.alpha = (215 * reveal).toInt()
        canvas.drawRoundRect(panel, 22f, 22f, p)
        p.shader = null
        p.style = Paint.Style.FILL
        for (i in 0..2) {
            p.color = controls[i]
            p.alpha = ((.88 + .12 * sin(time + i)) * 255 * reveal).toInt()
            canvas.drawCircle(97f + i * 25, 463f, 8f, p)
        }
        p.typeface = sans
        p.textSize = 17f
        p.textAlign = Paint.Align.CENTER
        p.color = 0xFFEAE6FF.toInt()
        p.alpha = (245 * reveal).toInt()
        canvas.drawText("AOS  /  VISORA LIVE CONSOLE", 561f, 470f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = mono
        p.textSize = textSize
        canvas.save()
        canvas.clipRect(92f, 499f, 1028f, 751f)
        val lineHeight = textSize + 7f
        val rowCount = (244f / lineHeight).toInt().coerceAtLeast(5)
        val skip = (lines.size + (if (typing != null) 1 else 0) - rowCount).coerceAtLeast(0)
        var row = 0
        var i = 0
        for (line in lines) {
            if (i++ < skip) continue
            line(canvas, line.text, line.level, 104f, 519 + row++ * lineHeight + scroll)
        }
        typing?.let { line(canvas, visibleText, it.level, 104f, 519 + row * lineHeight + scroll) }
        if ((time * 2).toInt() % 2 == 0) {
            p.color = 0xFFB2E9FF.toInt()
            p.alpha = (230 * reveal).toInt()
            val x = if (typing != null) 104 + p.measureText(visibleText) else 104f
            canvas.drawRect(
                x,
                503 + row * lineHeight + scroll,
                x + 8,
                522 + row * lineHeight + scroll,
                p,
            )
        }
        canvas.restore()
        p.typeface = mono
        p.textSize = 14f
        p.color = 0xFFB3AFD7.toInt()
        p.alpha = (225 * reveal).toInt()
        if (startup) {
            canvas.drawText("ACTUAL TASK COMPLETION", 104f, 783f, p)
            p.color = 0xFF252842.toInt()
            canvas.drawRoundRect(376f, 768f, 825f, 786f, 4f, 4f, p)
            p.color = 0xFF9D77FF.toInt()
            canvas.drawRoundRect(376f, 768f, 376 + 449 * smoothProgress / 100, 786f, 4f, 4f, p)
            p.color = 0xFFDAD7F6.toInt()
            canvas.drawText("${smoothProgress.toInt().coerceAtMost(progress)}%", 844f, 783f, p)
            p.color = if (phase == StartupPhase.DEGRADED) 0xFFFFC879.toInt() else 0xFF98EDBE.toInt()
            p.textSize = 11f
            canvas.drawText(
                if (phase == StartupPhase.DEGRADED) "WARNINGS"
                else if (progress == 100) "READY" else "WORKING",
                920f,
                782f,
                p,
            )
        } else {
            p.color = 0xFF70DCF3.toInt()
            canvas.drawText("aos@visora:~$", 104f, 783f, p)
            p.color = 0xFFA9A5C3.toInt()
            p.textSize = 12f
            canvas.drawText(
                if (isDrained) "MONITORING / waiting for real events" else "LIVE EVENT STREAM",
                295f,
                782f,
                p,
            )
            p.color = 0xFF8BEDCA.toInt()
            p.alpha = ((.4 + .6 * (.5 + .5 * sin(time * 2))) * 255 * reveal).toInt()
            canvas.drawCircle(1009f, 778f, 4f, p)
        }
        canvas.restore()
    }

    private fun line(canvas: Canvas, text: String, level: EventLevel, x: Float, y: Float) {
        p.color =
            when (level) {
                EventLevel.RUNNING -> 0xFFBBDEF4.toInt()
                EventLevel.OK -> 0xFF8CF4B5.toInt()
                EventLevel.WARNING -> 0xFFFFCC87.toInt()
                EventLevel.ERROR -> 0xFFFF829B.toInt()
            }
        p.alpha = (245 * reveal).toInt()
        canvas.drawText(text, x, y, p)
    }
}
