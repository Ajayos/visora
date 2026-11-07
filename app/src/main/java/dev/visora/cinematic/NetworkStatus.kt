package dev.visora.cinematic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.visora.NetworkMonitor
import dev.visora.TailscaleMonitor
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

class NetworkStatus(private val context: Context) {
    fun summary(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi =
            cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ==
                    true
            }
        val local = wifi?.let {
            cm.getLinkProperties(it)
                ?.linkAddresses
                ?.firstOrNull { a -> a.address is Inet4Address }
                ?.address
                ?.hostAddress
        }
        val tailnet = TailscaleMonitor(context).address()
        return "WI-FI ${if(wifi != null) local ?: "NO IPv4" else "DISCONNECTED"}   ·   VPN ${if(NetworkMonitor(context).vpnPresent()) "PRESENT" else "ABSENT"}   ·   TAILNET ${tailnet ?: "NO ADDRESS"}"
    }

    fun ssh(port: Int): String {
        if (port == 0) return "SSH NOT CONFIGURED"
        val address = TailscaleMonitor(context).address() ?: return "SSH NO TAILNET ADDRESS"
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(address, port), 1200)
                s.soTimeout = 1200
                val banner = ByteArray(64)
                val count = s.getInputStream().read(banner)
                if (count > 0 && String(banner, 0, count, Charsets.US_ASCII).startsWith("SSH-"))
                    "SSH LISTENING :$port"
                else "SSH NOT VERIFIED"
            }
        } catch (_: Exception) {
            "SSH UNAVAILABLE :$port"
        }
    }
}
