package dev.visora

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import dev.visora.cinematic.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val settings by lazy { SettingsManager(this) }
    private val engine
        get() = (application as VisoraApp).engine

    private lateinit var root: FrameLayout
    private lateinit var scene: CinematicScene
    private lateinit var weather: WeatherManager
    private var coordinator: StartupCoordinator? = null
    private var foreground = false
    private var destroyed = false
    private var generation = 0
    private var index = 0
    private var nextAt = Long.MAX_VALUE
    private var lastInput = 0L
    private var idle = false
    private val idleTimeout = Runnable { if (foreground && !idle) sleepDisplay() }

    private fun armIdleTimeout() {
        handler.removeCallbacks(idleTimeout)
        handler.postDelayed(idleTimeout, 300_000L)
    }

    private lateinit var blackout: View
    private var lastRevision = -1L
    private var activeSlides: List<Slide> = emptyList()
    private var bitmap: Bitmap? = null
    private var video: CachedVideoView? = null
    private var replayAfterSettings = false
    private var appliedConfig = AnimationConfig()
    private val listener: (RuntimeEvent) -> Unit = { event ->
        handler.post { if (foreground && !destroyed) scene.terminal.append(event) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DisplayManager(this).immersive()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        appliedConfig = configuration()
        scene =
            CinematicScene(this, appliedConfig) {
                    if (!destroyed && settings.token() != null)
                        startForegroundService(Intent(this, ManagementService::class.java))
                }
                .apply {
                    id = View.generateViewId()
                    isFocusable = true
                    defaultFocusHighlightEnabled = false
                    contentDescription =
                        "Animated AOS screen with Deluper branding and live system console"
                }
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adjustDisplay() }
        scene.requestFocus()
        startForegroundService(Intent(this, ServiceSupervisor::class.java))
        blackout =
            View(this).apply {
                setBackgroundColor(Color.BLACK)
                visibility = View.GONE
                contentDescription = "Idle black screen; television remains powered"
            }
        root.addView(blackout, FrameLayout.LayoutParams(-1, -1))
        scene.terminal.fontSize(settings.terminalSize)
        scene.onAmbient = {
            renderBackground()
        }
        coordinator =
            StartupCoordinator(
                    this,
                    engine,
                    settings.brand,
                    { id, logo -> if (!destroyed) scene.logo(id, logo) else logo?.recycle() },
                    { event -> if (!destroyed) scene.event(event) },
                    { if (!destroyed) scene.initializationFinished() },
                )
                .also { it.start() }
        weather =
            WeatherManager(this) { state ->
                scene.clock.weather = state.snapshot
                scene.clock.weatherLabel = state.label
            }
    }

    private fun adjustDisplay() {
        if (!::scene.isInitialized || root.width == 0 || root.height == 0) return
        val size =
            DisplayViewport.size(
                root.width,
                root.height,
                settings.displayWidth,
                settings.displayHeight,
                settings.displayFit,
            )
        val current = scene.layoutParams as FrameLayout.LayoutParams
        if (
            current.width != size.first ||
                current.height != size.second ||
                current.gravity != Gravity.CENTER
        ) {
            scene.layoutParams = FrameLayout.LayoutParams(size.first, size.second, Gravity.CENTER)
        }
    }

    private fun configuration(): AnimationConfig {
        val remote = engine.campaign?.animation ?: AnimationConfig()
        return remote.copy(
            enabled = remote.enabled && settings.cinematic,
            quality =
                if (settings.animationQuality == "auto") remote.quality
                else settings.animationQuality,
            motion =
                if (settings.reducedMotion) .15f
                else (remote.motion * settings.intensity).coerceIn(.1f, 1.5f),
            terminal = settings.terminalEnabled,
            typingCps = settings.typingSpeed,
            features = remote.features && settings.showFeatures,
        )
    }

    private fun replayIntro() {
        // Visual clock only. No coordinator restart, service restart, VPN action, or event history
        // reset.
        scene.replay()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        lastInput = SystemClock.elapsedRealtime()
        settings.mode = DisplayMode.PROMOTION
        DisplayManager(this).immersive()
        adjustDisplay()
        appliedConfig = configuration()
        scene.configure(appliedConfig, settings.terminalSize)
        scene.clock.format24Hour = settings.clock24
        RuntimeEvents.subscribe(listener)
        RuntimeEvents.snapshot().forEach { scene.terminal.append(it) }
        RuntimeEvents.publish(EventLevel.RUNNING, "lifecycle", "VISORA display entered foreground")
        idle = false
        blackout.visibility = View.GONE
        scene.resume()
        weather.start()
        armIdleTimeout()
        if (replayAfterSettings) {
            replayAfterSettings = false
            replayIntro()
        }
        if (scene.ambient) renderBackground()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        foreground = false
        RuntimeEvents.publish(
            EventLevel.RUNNING,
            "lifecycle",
            "VISORA display paused; animation and weather polling suspended",
        )
        RuntimeEvents.unsubscribe(listener)
        scene.pause()
        weather.pause()
        generation++
        releaseMedia()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(idleTimeout)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        coordinator?.cancel()
        weather.close()
        RuntimeEvents.unsubscribe(listener)
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        releaseMedia()
        scene.release()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 30 && resultCode == RESULT_OK)
            replayAfterSettings = data?.getBooleanExtra("replay", false) == true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (scene.ambient) renderBackground()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::scene.isInitialized) scene.trimMemory()
        if (!foreground) releaseMedia()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInput = SystemClock.elapsedRealtime()
        if (foreground && !idle) armIdleTimeout()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (idle && event.action == KeyEvent.ACTION_DOWN) {
            wakeDisplay()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sleepDisplay() {
        if (idle) return
        idle = true
        blackout.visibility = View.VISIBLE
        scene.pause()
        weather.pause()
        generation++
        releaseMedia()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        RuntimeEvents.publish(
            EventLevel.RUNNING,
            "display",
            "Five minutes idle: black fallback; graphics paused, TV power unchanged; diagnostics retained",
        )
    }

    private fun wakeDisplay() {
        idle = false
        lastInput = SystemClock.elapsedRealtime()
        blackout.visibility = View.GONE
        DisplayManager(this).immersive()
        scene.resume()
        weather.start()
        renderBackground()
        armIdleTimeout()
        RuntimeEvents.publish(EventLevel.RUNNING, "display", "Remote interaction restored display")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                startActivityForResult(Intent(this, SettingsActivity::class.java), 30)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                replayIntro()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private val tick =
        object : Runnable {
            override fun run() {
                if (!foreground) return
                val now = SystemClock.elapsedRealtime()
                if (idle) {
                    handler.postDelayed(this, 2000)
                    return
                }
                val revision = engine.campaign?.revision ?: -1L
                if (revision != lastRevision) {
                    lastRevision = revision
                    appliedConfig = configuration()
                    scene.configure(appliedConfig, settings.terminalSize)
                    scene.theme(engine.campaign?.theme ?: "midnight")
                    refreshLogos()
                    if (scene.ambient) renderBackground()
                } else if (
                    scene.ambient &&
                        (activeSlides != engine.slides(settings.brand) || now >= nextAt)
                ) {
                    index++
                    renderBackground()
                }
                handler.postDelayed(this, 2000)
            }
        }

    private fun refreshLogos() {
        if (engine.campaign?.brand?.logo.isNullOrEmpty()) return
        val campaign = engine.campaign ?: return
        worker.execute {
            val logo = runCatching {
                LogoRepository(this).load(campaign.brand.id, engine.content(campaign.brand.logo))
            }
                .getOrNull()
            handler.post {
                if (!destroyed) scene.logo(campaign.brand.id, logo) else logo?.recycle()
            }
        }
    }

    private fun renderBackground() {
        if (!scene.ambient || destroyed || idle) return
        generation++
        val ticket = generation
        releaseMedia()
        val campaign = engine.campaign?.takeIf { coordinator?.contentUsable != false }
        activeSlides = campaign?.active(System.currentTimeMillis()).orEmpty()
        index = if (activeSlides.isEmpty()) 0 else Math.floorMod(index, activeSlides.size)
        val slide = activeSlides.getOrNull(index)
        nextAt = SystemClock.elapsedRealtime() + (slide?.seconds ?: 60) * 1000L
        // Campaigns remain supported as optional media beneath the single unified live composition.
        if (slide?.kind == "image") {
            val image =
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = 0f
                }
            scene.mediaHost.addView(image, FrameLayout.LayoutParams(-1, -1))
            worker.execute {
                val decoded = runCatching { decode(engine.content(slide.sha256)) }.getOrNull()
                handler.post {
                    if (!destroyed && foreground && ticket == generation && decoded != null) {
                        bitmap = decoded
                        image.setImageBitmap(decoded)
                        image.animate().alpha(.32f).setDuration(900).start()
                    } else decoded?.recycle()
                }
            }
        }
        val hash =
            if (slide?.kind == "video") slide.sha256
            else campaign?.animation?.videoBackground.orEmpty()
        if (hash.isNotEmpty() && slide?.kind != "image" && foreground) {
            video =
                CachedVideoView(
                        this,
                        engine.content(hash),
                        slide?.kind != "video",
                        {
                            if (foreground && ticket == generation) {
                                index++
                                renderBackground()
                            }
                        },
                        {
                            RuntimeEvents.publish(
                                EventLevel.WARNING,
                                "media",
                                "Video unavailable; procedural background continues",
                            )
                        },
                    )
                    .also {
                        it.alpha = .28f
                        scene.mediaHost.addView(it, FrameLayout.LayoutParams(-1, -1))
                    }
        }
    }

    private fun releaseMedia() {
        video?.close()
        video = null
        if (::scene.isInitialized) scene.mediaHost.removeAllViews()
        bitmap?.recycle()
        bitmap = null
    }

    private fun decode(file: java.io.File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 720) sample *= 2
        return BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        )
    }
}
