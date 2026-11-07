package dev.visora

import android.content.Context
import android.os.SystemClock
import dev.visora.cinematic.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Uses exported supported requests only. Never starts another application's Activity or private
 * service.
 */
class BackgroundServices(private val context: Context) {
    private val settings = SettingsManager(context)
    private val vpn = RecoveryGate()
    private val ssh = SshController(context)
    private var discovery: AirScreenDiscovery? = null

    fun close() {
        ssh.stop()
        discovery?.close()
        discovery = null
    }

    private var wasConnected = false
    private var wasNetwork = false
    private var nextObservation = 0L

    fun tick(now: Long = SystemClock.elapsedRealtime()) {
        if (now < nextObservation) return
        nextObservation = now + 5000L
        if (settings.monitorCasting && discovery == null) discovery = AirScreenDiscovery(context)
        if (!settings.monitorCasting) {
            discovery?.close()
            discovery = null
        }
        ssh.tick(now)
        val network = NetworkMonitor(context).available()
        val connected =
            TailscaleMonitor(context).address() != null && NetworkMonitor(context).vpnPresent()
        if (connected && !wasConnected) {
            vpn.reset()
            RuntimeEvents.publish(
                EventLevel.OK,
                "vpn",
                "VPN transport/address observed; no application window opened",
            )
        }
        if (network && !wasNetwork) vpn.reset()
        wasConnected = connected
        wasNetwork = network
        if (settings.reconnect && network && !connected && vpn.due(now)) {
            vpn.requested(now)
            RuntimeEvents.publish(
                EventLevel.RUNNING,
                "vpn",
                "Background reconnect ${vpn.attempts}/3: ${TailscaleMonitor(context).requestReconnect()}",
            )
            if (vpn.attempts == 3)
                RuntimeEvents.publish(
                    EventLevel.WARNING,
                    "vpn",
                    "Background recovery budget exhausted; no foreground fallback; check authorization in Settings",
                )
        }
    }

    fun receiver(): String {
        if (!settings.monitorCasting) return "AirScreen monitoring disabled"
        val present = runCatching {
            context.packageManager.getPackageInfo("com.ionitech.airscreen", 0)
        }
            .isSuccess
        if (!present) return "AirScreen not installed"
        val n = NetworkDiagnostics(context).snapshot()
        if (!n.wifi || n.ipv4 == "No IPv4") return "AirScreen installed; no Wi-Fi IPv4 to probe"
        val endpoint =
            discovery?.endpoint
                ?: return "AirScreen installed; no local AirPlay advertisement observed yet"
        if (endpoint.address != n.ipv4) return "AirPlay observation stale after network change"
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(endpoint.address, endpoint.port), 1500)
                "AirPlay advertised and TCP :${endpoint.port} reachable; stream untested"
            }
        } catch (e: Exception) {
            "AirPlay advertised; TCP unavailable (${e.javaClass.simpleName}); private-service restart not assumed"
        }
    }
}
