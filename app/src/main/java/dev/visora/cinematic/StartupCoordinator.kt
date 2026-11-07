package dev.visora.cinematic

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.visora.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Performs observable real work. Intro timing never marks a service connected or a task complete.
 */
class StartupCoordinator(
    private val context: Context,
    private val engine: PromotionEngine,
    private val brand: String,
    private val logoReady: (String, Bitmap?) -> Unit,
    private val onEvent: (StartupEvent) -> Unit,
    private val onComplete: () -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    var contentUsable = true
        private set

    private val start = SystemClock.elapsedRealtime()

    private fun emit(step: StartupStep, level: EventLevel, text: String) {
        val event = StartupEvent(step, level, text, SystemClock.elapsedRealtime() - start)
        handler.post { if (!cancelled.get()) onEvent(event) }
    }

    private fun task(step: StartupStep, body: () -> Pair<EventLevel, String>) {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException()
        emit(step, EventLevel.RUNNING, step.prompt)
        val result =
            try {
                body()
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                EventLevel.ERROR to "${step.id}: ${e.javaClass.simpleName}; local fallback retained"
            }
        if (cancelled.get()) throw InterruptedException()
        emit(step, result.first, result.second)
    }

    fun start() {
        executor.execute {
            try {
                task(StartupStep.RUNTIME) {
                    SystemDiagnostics(context).crash()?.let {
                        RuntimeEvents.publish(
                            EventLevel.WARNING,
                            "recovery",
                            "Previous crash record: $it",
                        )
                    }
                    check(Build.VERSION.SDK_INT >= 28)
                    EventLevel.OK to "Kotlin runtime ready / Android API ${Build.VERSION.SDK_INT}"
                }
                task(StartupStep.VISUALS) {
                    for (id in listOf("aos", "deluper")) {
                        val override =
                            engine.campaign
                                ?.takeIf { it.brand.id == id }
                                ?.brand
                                ?.logo
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { engine.content(it) }
                        val bitmap = LogoRepository(context).load(id, override)
                        handler.post {
                            if (!cancelled.get()) logoReady(id, bitmap) else bitmap.recycle()
                        }
                    }
                    EventLevel.OK to "AOS and Deluper source assets decoded as independent layers"
                }
                task(StartupStep.SERVICES) {
                    val cast =
                        context.packageManager.getLaunchIntentForPackage(
                            "com.ionitech.airscreen",
                        ) != null ||
                            context.packageManager.getLeanbackLaunchIntentForPackage(
                                "com.ionitech.airscreen",
                            ) != null
                    val tailscale = runCatching {
                        context.packageManager.getPackageInfo("com.tailscale.ipn", 0)
                    }
                        .isSuccess
                    if (cast && tailscale)
                        EventLevel.OK to
                            "AirScreen and Tailscale installed; launch remains optional"
                    else
                        EventLevel.WARNING to
                            "Optional service missing / AirScreen=$cast Tailscale=$tailscale"
                }
                task(StartupStep.NETWORK) {
                    var available = NetworkMonitor(context).available()
                    for (attempt in 1..3) {
                        if (available) break
                        emit(StartupStep.NETWORK, EventLevel.RUNNING, "Network retry $attempt of 3")
                        Thread.sleep(1000)
                        available = NetworkMonitor(context).available()
                    }
                    if (available)
                        EventLevel.OK to
                            "Network available; internet/server reachability not assumed"
                    else EventLevel.WARNING to "Network unavailable / continuing with local content"
                }
                task(StartupStep.VPN) {
                    val address = TailscaleMonitor(context).address()
                    if (address != null)
                        EventLevel.OK to
                            "Tailnet address present: $address (peer reachability untested)"
                    else {
                        val message =
                            if (SettingsManager(context).reconnect)
                                "No tailnet address; central supervisor owns bounded background recovery"
                            else "No tailnet address; reconnect not enabled"
                        EventLevel.WARNING to message
                    }
                }
                task(StartupStep.CORE) {
                    val settings = SettingsManager(context)
                    if (settings.origin.isEmpty() || settings.token() == null)
                        EventLevel.WARNING to
                            "AOS Core not enrolled / local display remains available"
                    else if (!NetworkMonitor(context).available())
                        EventLevel.WARNING to "AOS Core unreachable / network unavailable"
                    else {
                        try {
                            RemoteManagementClient(context).checkConnection()
                            EventLevel.OK to "AOS Core authenticated device status verified"
                        } catch (e: Exception) {
                            BackendConnection.failed()
                            EventLevel.WARNING to
                                "AOS Core not verified (${e.javaClass.simpleName}) / cached display"
                        }
                    }
                }
                task(StartupStep.CONTENT) {
                    contentUsable = false
                    val campaign = engine.campaign?.takeIf { it.brand.id == brand }
                    if (campaign == null) {
                        BrandingManager(context).bundled(brand)
                        contentUsable = true
                        EventLevel.OK to "Independent bundled $brand content loaded"
                    } else {
                        val assets =
                            (campaign.slides.filter { it.kind != "text" }.map { it.sha256 } +
                                    listOf(campaign.brand.logo, campaign.animation.videoBackground))
                                .filter { it.isNotEmpty() }
                                .distinct()
                        var invalid = 0
                        for ((i, hash) in assets.withIndex()) {
                            if (cancelled.get()) throw InterruptedException()
                            val file = engine.content(hash)
                            if (!file.isFile || PromotionEngine.digest(file) != hash) invalid++
                            emit(
                                StartupStep.CONTENT,
                                EventLevel.RUNNING,
                                "Validated ${i+1}/${assets.size} cached assets",
                            )
                        }
                        contentUsable = invalid == 0
                        if (invalid == 0)
                            EventLevel.OK to
                                "Campaign revision ${campaign.revision} / ${assets.size} cached assets verified"
                        else
                            EventLevel.WARNING to
                                "$invalid cached assets invalid / bundled fallback selected"
                    }
                }
                task(StartupStep.DISPLAY) {
                    check(context.resources.displayMetrics.widthPixels > 0)
                    EventLevel.OK to "VISORA display initialized / promotional mode available"
                }
                handler.post { if (!cancelled.get()) onComplete() }
            } catch (_: InterruptedException) {
                /* Activity was destroyed; do not publish stale completion. */
            } finally {
                executor.shutdown()
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }
}
