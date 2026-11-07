package dev.visora.cinematic

import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.View
import kotlin.math.min

/** One vsync clock for all independent layers. No timers, bitmaps or coroutines per particle. */
class AnimationEngine(
    private val config: AnimationConfig,
    private val frame: (Float, Float, Int) -> Unit,
) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var last = 0L
    private var drawn = 0L
    private var activeNanos = 0L
    private var interval = 1_000_000_000L / config.fps
    private var quality =
        when (config.quality) {
            "low" -> 0
            "high" -> 2
            else -> 1
        }
    private var automatic = config.quality == "auto"
    private var slowFrames = 0
    private var sampled = 0
    var renderedFrames = 0L
        private set

    var elapsed = 0f
        private set

    var averageIntervalMs = 0f
        private set

    val currentQuality
        get() = quality

    val isRunning
        get() = running

    fun start() {
        if (running) return
        running = true
        last = 0
        drawn = 0
        choreographer.postFrameCallback(this)
    }

    fun pause() {
        running = false
        last = 0
        drawn = 0
        choreographer.removeFrameCallback(this)
    }

    fun configure(mode: String, fps: Int) {
        automatic = mode == "auto"
        quality =
            when (mode) {
                "low" -> 0
                "high" -> 2
                else -> 1
            }
        interval = 1_000_000_000L / fps
    }

    fun replay() {
        val wasRunning = running
        pause()
        activeNanos = 0L
        elapsed = 0f
        if (wasRunning) start()
    }

    fun reduceQuality() {
        quality = 0
        interval = 1_000_000_000L / 20
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (last != 0L) activeNanos += (frameTimeNanos - last).coerceAtMost(100_000_000L)
        last = frameTimeNanos
        if (drawn == 0L || frameTimeNanos - drawn >= interval - 1_000_000L) {
            val dt = if (drawn == 0L) 1f / config.fps else (frameTimeNanos - drawn) / 1_000_000_000f
            drawn = frameTimeNanos
            elapsed = activeNanos / 1_000_000_000f
            renderedFrames++
            averageIntervalMs =
                if (renderedFrames == 1L) dt * 1000 else averageIntervalMs * .95f + dt * 50
            if (dt > .052f) slowFrames++
            sampled++
            if (sampled >= 90 && automatic) {
                if (slowFrames > 16) {
                    quality = (quality - 1).coerceAtLeast(0)
                    if (quality == 0) interval = 1_000_000_000L / 24
                }
                sampled = 0
                slowFrames = 0
            }
            frame(elapsed, min(dt, .1f), quality)
        }
        if (running) choreographer.postFrameCallback(this)
    }
}

abstract class SceneLayer(context: Context) : View(context) {
    protected var time = 0f
    protected var quality = 1
    protected var motion = 1f
    var reveal = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    open fun frame(seconds: Float, delta: Float, quality: Int, motion: Float) {
        time = seconds
        this.quality = quality
        this.motion = motion
        if (visibility == VISIBLE && reveal > 0f) invalidate()
    }

    final override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (reveal <= 0f) return
        val save = canvas.save()
        canvas.scale(width / 1920f, height / 1080f)
        drawScene(canvas)
        canvas.restoreToCount(save)
    }

    protected abstract fun drawScene(canvas: Canvas)

    open fun release() {}
}
