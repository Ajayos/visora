package dev.visora

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.visora.cinematic.*
import java.util.ArrayDeque

/**
 * Resolves a real local AirPlay advertisement; never guesses receiver ports or starts a private
 * service.
 */
class AirScreenDiscovery(private val context: Context) {
    data class Endpoint(val name: String, val address: String, val port: Int)

    @Volatile
    var endpoint: Endpoint? = null
        private set

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    @Volatile private var started = false
    @Volatile private var closed = false
    private val listener =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                started = true
                if (closed) runCatching { nsd.stopServiceDiscovery(this) }
            }

            override fun onDiscoveryStopped(type: String) {
                started = false
            }

            override fun onStartDiscoveryFailed(type: String, error: Int) {
                RuntimeEvents.publish(
                    EventLevel.WARNING,
                    "casting",
                    "AirPlay discovery unavailable (NSD $error)",
                )
            }

            override fun onStopDiscoveryFailed(type: String, error: Int) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(pending) { if (!closed && pending.size < 16) pending.add(info) }
                resolveNext()
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                if (endpoint?.name == info.serviceName) {
                    endpoint = null
                    RuntimeEvents.publish(
                        EventLevel.WARNING,
                        "casting",
                        "Local AirPlay advertisement removed",
                    )
                }
            }
        }

    init {
        runCatching { nsd.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure {
                RuntimeEvents.publish(
                    EventLevel.WARNING,
                    "casting",
                    "AirPlay NSD discovery could not start",
                )
            }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val info =
            synchronized(pending) {
                if (closed || resolving || pending.isEmpty()) return
                resolving = true
                pending.removeFirst()
            }
        try {
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                        synchronized(pending) { resolving = false }
                        resolveNext()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val address = info.host?.hostAddress
                        if (
                            !closed &&
                                address == NetworkDiagnostics(context).snapshot().ipv4 &&
                                info.port in 1..65535
                        ) {
                            endpoint = Endpoint(info.serviceName, address, info.port)
                            RuntimeEvents.publish(
                                EventLevel.OK,
                                "casting",
                                "Local AirPlay advertised: ${info.serviceName} :${info.port}; stream test separate",
                            )
                        }
                        synchronized(pending) { resolving = false }
                        resolveNext()
                    }
                },
            )
        } catch (_: Exception) {
            synchronized(pending) { resolving = false }
            resolveNext()
        }
    }

    fun close() {
        closed = true
        if (started) runCatching { nsd.stopServiceDiscovery(listener) }
        synchronized(pending) { pending.clear() }
        endpoint = null
    }
}
