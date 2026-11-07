package dev.visora

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun available(): Boolean =
        cm.allNetworks.any {
            cm.getNetworkCapabilities(it)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

    fun vpnPresent(): Boolean =
        cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
}

class TailscaleMonitor(private val context: Context) {
    fun address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }
            .firstOrNull {
                val p = it.split('.').mapNotNull(String::toIntOrNull)
                p.size == 4 && p[0] == 100 && p[1] in 64..127
            }
    }
        .getOrNull()

    // This detects a local address, not peer reachability or VPN ownership.
    fun requestReconnect(): String {
        val component = ComponentName("com.tailscale.ipn", "com.tailscale.ipn.IPNReceiver")
        return try {
            val info = context.packageManager.getReceiverInfo(component, 0)
            if (!info.exported || !info.enabled || info.permission != null)
                return "Tailscale receiver unavailable; open Tailscale"
            context.sendBroadcast(Intent("com.tailscale.ipn.CONNECT_VPN").setComponent(component))
            "Reconnect requested; connection not yet verified"
        } catch (_: Exception) {
            "Tailscale reconnect unavailable; open Tailscale"
        }
    }
}

class CastingController(private val context: Context) {
    fun launch(packageName: String = "com.ionitech.airscreen"): Boolean {
        if (packageName !in SettingsManager(context).allowedApps) return false
        val intent =
            context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
