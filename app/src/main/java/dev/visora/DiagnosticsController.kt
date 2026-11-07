package dev.visora

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.visora.cinematic.*
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class DiagnosticSnapshot(
    val network: NetworkSnapshot? = null,
    val internet: String = "Not yet probed",
    val latency: Long? = null,
    val ssh: String = "Not checked",
    val backend: String = "Not checked",
    val memory: String = "Sampling",
    val cpu: String = "Sampling",
    val storage: String = "Sampling",
    val casting: String = "Not checked",
)

/**
 * One serial scheduler owned by the foreground supervisor. Every output is an actual observation,
 * not a decorative log.
 */
class DiagnosticsController(
    private val context: Context,
    private val update: (DiagnosticSnapshot) -> Unit,
    private val background: BackgroundServices? = null,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val settings = SettingsManager(context)
    private val system = SystemDiagnostics(context)
    private val network = NetworkDiagnostics(context)
    private val cpu = CpuSampler()
    private var schedule = DiagnosticSchedule(settings.diagnosticPolicy)
    private var configured = settings.diagnosticPolicy
    private var future: ScheduledFuture<*>? = null
    @Volatile private var active = false
    private var current = DiagnosticSnapshot()
    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    private val callback =
        object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                event(
                    EventLevel.RUNNING,
                    "network",
                    "Network became available; next full cycle verifies reachability",
                )
                refreshNetwork()
            }

            override fun onLost(network: android.net.Network) {
                event(EventLevel.WARNING, "network", "Network lost")
                refreshNetwork()
            }

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                capabilities: android.net.NetworkCapabilities,
            ) {
                refreshNetwork()
            }
        }
    private var networkFailures = 0
    private var probeAfter = 0L
    private var backendAfter = 0L
    private var backendFailures = 0
    val running
        get() = active

    fun start() {
        if (active) return
        active = true
        refreshNetwork()
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        future =
            executor.scheduleWithFixedDelay(
                {
                    try {
                        tick()
                    } catch (e: Exception) {
                        event(
                            EventLevel.ERROR,
                            "monitor",
                            "Diagnostic cycle failed (${e.javaClass.simpleName}); scheduler retained",
                        )
                    }
                },
                0,
                2,
                TimeUnit.SECONDS,
            )
        RuntimeEvents.publish(
            EventLevel.RUNNING,
            "monitor",
            "Continuous diagnostics started; actual checks scheduled",
        )
    }

    // Cheap interface observation is event-driven; full probes and CPU/storage sampling remain
    // five-minute tasks.
    private fun refreshNetwork() {
        if (!active) return
        runCatching {
            executor.execute {
                if (!active) return@execute
                check("network") {
                    val snapshot = network.snapshot()
                    current =
                        current.copy(
                            network = snapshot,
                            ssh =
                                if (current.network?.tailnet != snapshot.tailnet)
                                    "Awaiting scheduled SSH probe"
                                else current.ssh,
                        )
                    val value = current
                    handler.post { if (active) update(value) }
                }
            }
        }
    }

    fun pause() {
        if (!active) return
        active = false
        runCatching { cm.unregisterNetworkCallback(callback) }
        future?.cancel(true)
        future = null
        handler.removeCallbacksAndMessages(null)
    }

    fun close() {
        pause()
        executor.shutdownNow()
    }

    private fun event(level: EventLevel, source: String, text: String) {
        if (active) RuntimeEvents.publish(level, source, text)
    }

    private fun check(source: String, task: () -> Unit) {
        if (!active) return
        try {
            task()
        } catch (e: Exception) {
            event(
                EventLevel.WARNING,
                source,
                "$source check unavailable (${e.javaClass.simpleName})",
            )
        }
    }

    private fun tick() {
        if (!active) return
        val now = SystemClock.elapsedRealtime()
        background?.tick(now)
        val policy = settings.diagnosticPolicy
        if (policy != configured) {
            configured = policy
            schedule = DiagnosticSchedule(policy)
        }
        if (schedule.due("memory", now))
            check("memory") {
                val m = system.memory()
                val text =
                    "RAM ${m.available/1048576}/${m.total/1048576} MiB available / app ${m.processKb/1024} MiB PSS"
                current = current.copy(memory = text)
                event(if (m.low) EventLevel.WARNING else EventLevel.OK, "memory", text)
            }
        if (schedule.due("cpu", now))
            check("cpu") {
                val sample = cpu.sample()
                val text =
                    "VISORA CPU ${sample.processPercent?.let { "%.1f%% of one core".format(Locale.ROOT,it) } ?: "baseline sampled"} / device CPU ${sample.systemPercent?.let { "%.1f%%".format(Locale.ROOT,it) } ?: "restricted or awaiting second sample"}"
                current = current.copy(cpu = text)
                event(EventLevel.RUNNING, "cpu", text)
            }
        if (schedule.due("wifi", now))
            check("network") {
                val snapshot = network.snapshot()
                current = current.copy(network = snapshot)
                event(
                    if (snapshot.wifi) EventLevel.OK else EventLevel.WARNING,
                    "network",
                    "${snapshot.connectionType} / ${snapshot.ipv4} / gateway ${snapshot.gateway} / RSSI ${snapshot.rssi?.let { "$it dBm" } ?: "unavailable"}",
                )
            }
        if (schedule.due("vpn", now))
            check("vpn") {
                val snapshot = network.snapshot()
                event(
                    if (snapshot.vpn && snapshot.tailnet != null) EventLevel.OK
                    else EventLevel.WARNING,
                    "vpn",
                    "VPN transport ${if(snapshot.vpn) "present" else "absent"}; assigned tailnet ${snapshot.tailnet ?: "none"}; peer reachability separate",
                )
            }
        if (schedule.due("latency", now) && now >= probeAfter)
            check("internet") {
                val result = network.reachability(settings.internetProbe)
                networkFailures = if (result.reachable) 0 else (networkFailures + 1).coerceAtMost(4)
                probeAfter =
                    now +
                        if (result.reachable) policy.latency
                        else (30_000L shl networkFailures).coerceAtMost(300_000)
                current = current.copy(internet = result.detail, latency = result.latencyMs)
                event(
                    if (result.reachable) EventLevel.OK else EventLevel.WARNING,
                    "internet",
                    "${result.detail}${result.latencyMs?.let { " / $it ms" } ?: ""}",
                )
            }
        if (schedule.due("ssh", now))
            check("ssh") {
                val value = NetworkStatus(context).ssh(settings.sshPort)
                current = current.copy(ssh = value)
                event(
                    if (value.contains("LISTENING")) EventLevel.OK else EventLevel.WARNING,
                    "ssh",
                    value,
                )
            }
        if (schedule.due("backend", now) && now >= backendAfter)
            check("backend") {
                if (settings.deviceId.isEmpty() || settings.token() == null) {
                    current = current.copy(backend = "Not enrolled")
                    event(
                        EventLevel.RUNNING,
                        "backend",
                        "AOS Core not configured; cached display remains available",
                    )
                } else {
                    try {
                        RemoteManagementClient(context).checkConnection()
                        backendFailures = 0
                        current = current.copy(backend = "Authenticated status verified")
                        event(
                            EventLevel.OK,
                            "backend",
                            "AOS Core device identity verified over tailnet HTTPS",
                        )
                    } catch (e: Exception) {
                        BackendConnection.failed()
                        backendFailures = (backendFailures + 1).coerceAtMost(4)
                        backendAfter = now + (30_000L shl backendFailures).coerceAtMost(300_000)
                        current = current.copy(backend = "Unreachable / unverified")
                        event(
                            EventLevel.WARNING,
                            "backend",
                            "Core status unavailable (${e.javaClass.simpleName}); retry backs off",
                        )
                    }
                }
            }
        if (schedule.due("storage", now))
            check("storage") {
                val bytes = system.storage()
                val text = "${bytes/1048576} MiB available on app data volume"
                current = current.copy(storage = text)
                event(if (bytes < 104857600) EventLevel.WARNING else EventLevel.OK, "storage", text)
            }
        if (schedule.due("health", now))
            check("health") {
                val casting = background?.receiver() ?: "Receiver supervision unavailable"
                current = current.copy(casting = casting)
                event(
                    if (casting.contains("reachable")) EventLevel.OK else EventLevel.WARNING,
                    "casting",
                    casting,
                )
                event(EventLevel.RUNNING, "health", system.health())
            }
        val snapshot = current
        handler.post { if (active) update(snapshot) }
    }
}
