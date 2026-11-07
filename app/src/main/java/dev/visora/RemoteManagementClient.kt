package dev.visora

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Proposed device protocol. Must be matched to the real AOS Core API before enrollment. */
class RemoteManagementClient(private val context: Context) {
    private val settings = SettingsManager(context)
    @Volatile
    var status = "Not registered"
        private set

    private fun connection(url: String): HttpsURLConnection {
        val uri = SecurityPolicy.https(url)
        require(uri.host == SecurityPolicy.tailnetEndpoint(settings.origin).host) {
            "Cross-origin request refused"
        }
        require(uri.port == SecurityPolicy.https(settings.origin).port)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        require(TailscaleMonitor(context).address() != null) { "No tailnet address" }
        val vpn =
            cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ==
                    true
            } ?: error("No VPN transport")
        return (vpn.openConnection(uri.toURL()) as HttpsURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
        }
    }

    private fun request(
        path: String,
        body: JSONObject? = null,
        enrollment: String? = null,
    ): JSONObject {
        val c = connection(settings.origin + path)
        try {
            c.requestMethod = if (body == null) "GET" else "POST"
            c.setRequestProperty(
                "Authorization",
                "Bearer ${enrollment ?: settings.token() ?: error("Device not registered")}",
            )
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(c.responseCode in 200..299) { "Server HTTP ${c.responseCode}" }
            val bytes = c.inputStream.use { it.readBytesLimited(512 * 1024) }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            c.disconnect()
        }
    }

    fun register(oneTimeCode: String) {
        require(oneTimeCode.length in 8..512)
        val response =
            request(
                "/api/visora/v1/register",
                JSONObject()
                    .put("model", android.os.Build.MODEL)
                    .put(
                        "appVersion",
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                    ),
                oneTimeCode,
            )
        val id = response.getString("deviceId")
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(id))
        val commandKey = response.getString("commandPublicKey")
        KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(commandKey, Base64.NO_WRAP)))
            .also {
                require((it as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 2048)
            }
        val token = response.getString("token")
        require(token.length in 16..4096)
        settings.deviceId = id
        settings.commandKey = commandKey
        settings.storeToken(token)
        status = "Registered; remote commands disabled until enabled locally"
    }

    /**
     * Read-only proposed device-status endpoint; no command reservation or fabricated connection
     * state.
     */
    fun checkConnection() {
        require(settings.deviceId.isNotEmpty())
        val result = request("/api/visora/v1/devices/${settings.deviceId}/status")
        require(result.getString("deviceId") == settings.deviceId) { "Status identity mismatch" }
        dev.visora.cinematic.BackendConnection.reached()
    }

    fun download(url: String, file: File, max: Long) {
        val c = connection(url)
        try {
            c.setRequestProperty(
                "Authorization",
                "Bearer ${settings.token() ?: error("Not registered")}",
            )
            check(c.responseCode == 200) { "Media HTTP ${c.responseCode}" }
            require(c.contentLengthLong <= max)
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(32768)
                    var total = 0L
                    val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
                    while (true) {
                        check(android.os.SystemClock.elapsedRealtime() < deadline) {
                            "Download timeout"
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= max)
                        out.write(buffer, 0, n)
                    }
                    out.fd.sync()
                }
            }
        } finally {
            c.disconnect()
        }
    }

    fun poll(engine: PromotionEngine, execute: (String, JSONObject) -> String) {
        if (settings.origin.isEmpty() || settings.token() == null) {
            status = "Not registered"
            return
        }
        val base = "/api/visora/v1/devices/${settings.deviceId}"
        val response =
            request(
                "$base/sync",
                JSONObject()
                    .put("health", DeviceHealthMonitor(context).snapshot())
                    .put("revision", engine.campaign?.revision ?: 0)
                    .put("sequence", settings.sequence),
            )
        response.optJSONObject("campaign")?.let {
            if (it.getLong("revision") > (engine.campaign?.revision ?: 0))
                engine.install(it, ::download)
        }
        val commands = response.optJSONArray("commands")
        if (commands != null) {
            require(commands.length() <= 20)
            for (i in 0 until commands.length()) {
                val envelope = commands.getJSONObject(i)
                val cmd = CommandVerifier.verify(envelope, settings.commandKey, settings.deviceId)
                val seq = cmd.getLong("sequence")
                if (seq <= settings.sequence) continue
                require(seq > 0)
                val type = cmd.getString("type")
                // Reserve before execution: at-most-once even after a crash. Administrators retry
                // with a new sequence.
                settings.sequence = seq
                val result =
                    if (
                        SecurityPolicy.commandAllowed(
                            type,
                            cmd.getLong("expiresAt"),
                            System.currentTimeMillis(),
                            settings.remoteEnabled,
                        )
                    ) {
                        runCatching { execute(type, cmd.optJSONObject("args") ?: JSONObject()) }
                            .getOrElse { "failed:${it.javaClass.simpleName}" }
                    } else "rejected:authorization_or_expiry"
                request("$base/ack", JSONObject().put("sequence", seq).put("result", result))
            }
        }
        status = "AOS Core reached securely"
    }
}

fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(out.size() + n <= max) { "Response too large" }
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
