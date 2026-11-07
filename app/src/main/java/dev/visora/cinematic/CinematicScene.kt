package dev.visora.cinematic

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout

/**
 * The single reference composition: Deluper top-left, AOS hero, live clock and continuous console.
 */
class CinematicScene(
    context: Context,
    initialConfig: AnimationConfig,
    private val onReady: () -> Unit,
) : FrameLayout(context) {
    var config = initialConfig
        private set

    private val layers = mutableListOf<SceneLayer>()

    private fun <T : SceneLayer> layer(view: T): T {
        layers.add(view)
        addView(view, LayoutParams(-1, -1))
        return view
    }

    val galaxy = GalaxySurface(context, config).also { addView(it, LayoutParams(-1, -1)) }
    val mediaHost = FrameLayout(context).also { addView(it, LayoutParams(-1, -1)) }
    private val orbit = layer(OrbitLayer(context))
    private val particles = layer(ParticleLayer(context))
    val branding = layer(BrandingLayer(context).apply { brandId = "aos" })
    private val deluper = layer(BrandingLayer(context, true).apply { brandId = "deluper" })
    val terminal = layer(LiveTerminalView(context, config))
    private val icons = Array(6) { layer(TechnologyIconLayer(context, it)) }
    val clock = layer(GlassClockView(context))
    val state = StartupStateMachine()
    var ambient = !config.enabled
        private set

    var ready = false
        private set

    var onAmbient: (() -> Unit)? = null
    private val logos = mutableMapOf<String, Bitmap>()
    private var released = false
    private var replaying = false
    private var externallyPaused = true
    private var lastDiagnosticSecond = -1
    val animation = AnimationEngine(config, ::frame)

    init {
        setBackgroundColor(0xFF020513.toInt())
        applyTimeline(0f)
    }

    fun logo(brand: String, bitmap: Bitmap?) {
        val previous = logos.remove(brand)
        if (bitmap != null) logos[brand] = bitmap
        if (brand == "aos") branding.setLogo(bitmap, brand) else deluper.setLogo(bitmap, brand)
        if (previous != bitmap) previous?.recycle()
    }

    fun event(event: StartupEvent) {
        state.accept(event)
        terminal.taskProgress(state.progress, state.phase)
        RuntimeEvents.publish(event.level, "startup", event.message)
    }

    fun configure(value: AnimationConfig, fontSize: Int) {
        config = value
        terminal.typingCps = value.typingCps
        terminal.fontSize(fontSize)
        galaxy.galaxy.motion = value.motion
        galaxy.galaxy.requestedStars = value.stars
        animation.configure(value.quality, value.fps)
    }

    fun initializationFinished() {
        ready = true
        onReady()
    }

    fun replay() {
        replaying = true
        ambient = false
        animation.replay()
        RuntimeEvents.publish(
            EventLevel.RUNNING,
            "display",
            "Visual intro replay requested; services and log history preserved",
        )
    }

    private fun publishDiagnostics() {
        CinematicDiagnostics.snapshot =
            SceneDiagnostics(
                animation.renderedFrames,
                animation.elapsed,
                animation.averageIntervalMs,
                animation.currentQuality,
                animation.isRunning,
                isHardwareAccelerated,
                state.phase.name,
                ambient,
                layers.size + 4,
                galaxy.galaxy.renderedFrames,
                galaxy.galaxy.measuredFps,
                galaxy.galaxy.glReady,
                galaxy.galaxy.glError,
            )
    }

    private fun frame(seconds: Float, delta: Float, quality: Int) {
        applyTimeline(seconds)
        galaxy.frame(seconds, quality)
        for (layer in layers) layer.frame(seconds, delta, quality, config.motion)
        if (seconds.toInt() != lastDiagnosticSecond) {
            lastDiagnosticSecond = seconds.toInt()
            publishDiagnostics()
        }
    }

    private fun applyTimeline(seconds: Float) {
        val t = if (config.enabled || replaying) seconds else seconds + config.introSeconds
        galaxy.galaxy.skyReveal = SceneTimeline.reveal(t, 0f, 2f)
        galaxy.galaxy.starReveal = SceneTimeline.reveal(t, 0f, 2f)
        galaxy.galaxy.nebulaReveal = SceneTimeline.reveal(t, .4f, 2.3f)
        galaxy.galaxy.planetReveal = SceneTimeline.reveal(t, .8f, 2.5f)
        galaxy.galaxy.waveReveal = SceneTimeline.reveal(t, 4f, 3f)
        deluper.reveal = SceneTimeline.reveal(t, 2f, 2f)
        branding.reveal = SceneTimeline.reveal(t, 3f, 3f)
        orbit.reveal = SceneTimeline.reveal(t, 4f, 3f)
        particles.reveal = SceneTimeline.reveal(t, 1f, 2f)
        particles.forming = !ambient
        val resolved = state.phase in setOf(StartupPhase.READY, StartupPhase.DEGRADED)
        clock.reveal = if (resolved) SceneTimeline.reveal(t, 8f, 2f) else 0f
        for (i in icons.indices) icons[i].reveal =
            if (config.features && resolved) SceneTimeline.reveal(t, 8.5f + i * .18f, .8f) else 0f
        if (!ambient && SceneTimeline.canEnterAmbient(seconds, config, state.phase, true)) {
            ambient = true
            replaying = false
            onAmbient?.invoke()
        }
        terminal.startup = !ambient
        terminal.reveal = if (config.terminal) SceneTimeline.reveal(t, 5f, 1f) else 0f
    }

    fun theme(id: String) {
        galaxy.galaxy.warm = id == "warm"
    }

    fun resume() {
        externallyPaused = false
        if (windowVisibility == VISIBLE && !released) {
            galaxy.resumeRendering()
            animation.start()
        }
    }

    fun pause() {
        externallyPaused = true
        animation.pause()
        galaxy.pauseRendering()
        publishDiagnostics()
    }

    fun trimMemory() {
        animation.reduceQuality()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!externallyPaused && visibility == VISIBLE && !released) {
            galaxy.resumeRendering()
            animation.start()
        } else {
            animation.pause()
            galaxy.pauseRendering()
        }
    }

    override fun onDetachedFromWindow() {
        animation.pause()
        galaxy.pauseRendering()
        super.onDetachedFromWindow()
    }

    fun release() {
        if (released) return
        released = true
        animation.pause()
        galaxy.pauseRendering()
        for (layer in layers) layer.release()
        branding.setLogo(null, "aos")
        deluper.setLogo(null, "deluper")
        logos.values.forEach { it.recycle() }
        logos.clear()
    }
}
