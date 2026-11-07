package dev.visora

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class NetworkSnapshot(
    val wifi: Boolean,
    val ssid: String,
    val ipv4: String,
    val gateway: String,
    val dns: String,
    val connectionType: String,
    val rssi: Int?,
    val interfaceName: String,
    val vpn: Boolean,
    val tailnet: String?,
    val validated: Boolean,
) {
    fun summary() =
        "${if(wifi) "Wi-Fi" else connectionType} $ipv4 · VPN ${if(vpn) "present" else "absent"} · tailnet ${tailnet ?: "none"}"
}

data class Reachability(val reachable: Boolean, val latencyMs: Long?, val detail: String)

class NetworkDiagnostics(private val context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    fun snapshot(): NetworkSnapshot {
        val networks = cm.allNetworks
        val wifi = networks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val active = cm.activeNetwork
        val properties = (wifi ?: active)?.let { cm.getLinkProperties(it) }
        val caps = (wifi ?: active)?.let { cm.getNetworkCapabilities(it) }
        val info =
            if (wifi != null)
                runCatching {
                    (context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                            as WifiManager)
                        .connectionInfo
                }
                    .getOrNull()
            else null
        val ssidAllowed =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        val ssid =
            if (ssidAllowed)
                info
                    ?.ssid
                    ?.trim('"')
                    ?.takeUnless { it == "<unknown ssid>" }
                    .orEmpty()
                    .ifBlank { "Unavailable" }
            else "Permission restricted"
        val rssi = info?.rssi?.takeIf { it in -126..0 }
        val type =
            when {
                wifi != null -> "WI-FI"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                active == null -> "DISCONNECTED"
                else -> "OTHER"
            }
        return NetworkSnapshot(
            wifi != null,
            ssid,
            properties
                ?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
                ?.address
                ?.hostAddress ?: "No IPv4",
            properties
                ?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway
                ?.hostAddress ?: "Unavailable",
            properties?.dnsServers?.joinToString(", ") { it.hostAddress.orEmpty() }
                ?: "Unavailable",
            type,
            rssi,
            properties?.interfaceName ?: "none",
            NetworkMonitor(context).vpnPresent(),
            TailscaleMonitor(context).address(),
            active?.let {
                cm.getNetworkCapabilities(it)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true,
        )
    }

    fun reachability(endpoint: String): Reachability {
        SecurityPolicy.https(endpoint)
        val start = android.os.SystemClock.elapsedRealtime()
        var c: HttpsURLConnection? = null
        return try {
            c = URL(endpoint).openConnection() as HttpsURLConnection
            c.connectTimeout = 2500
            c.readTimeout = 2500
            c.requestMethod = "HEAD"
            c.instanceFollowRedirects = false
            val status = c.responseCode
            val elapsed = android.os.SystemClock.elapsedRealtime() - start
            if (status in 200..299) Reachability(true, elapsed, "HTTPS probe HTTP $status")
            else Reachability(false, elapsed, "Probe HTTP $status; internet not verified")
        } catch (e: Exception) {
            Reachability(false, null, "HTTPS probe unavailable (${e.javaClass.simpleName})")
        } finally {
            c?.disconnect()
        }
    }
}
