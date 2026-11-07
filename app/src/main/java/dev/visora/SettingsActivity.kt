package dev.visora

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import dev.visora.cinematic.*
import java.util.concurrent.Executors

class SettingsActivity : Activity() {
    private val settings by lazy { SettingsManager(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var panel: LinearLayout
    private var advanced = false

    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DisplayManager(this).immersive()
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF080C1C.toInt()) }
        panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(48), dp(28), dp(48), dp(28))
            }
        scroll.addView(panel)
        setContentView(scroll)
        build()
    }

    private fun text(value: String, size: Float = 15f) {
        panel.addView(
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(0xFFD8D9EE.toInt())
                setPadding(0, dp(9), 0, dp(9))
            },
        )
    }

    private fun button(value: String, action: () -> Unit) {
        panel.addView(
            Button(this).apply {
                text = value
                textSize = 14f
                isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.WHITE)
                setPadding(dp(18), 0, dp(18), 0)
                background =
                    DisplayManager.rounded(0xFF1B1C38.toInt(), dp(10).toFloat(), 0xFF41436A.toInt())
                setOnFocusChangeListener { view, focused ->
                    view.background =
                        DisplayManager.rounded(
                            if (focused) 0xFF353060.toInt() else 0xFF1B1C38.toInt(),
                            dp(10).toFloat(),
                            if (focused) 0xFF9FD6FF.toInt() else 0xFF41436A.toInt(),
                        )
                }
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(-1, dp(47)).apply { bottomMargin = dp(6) },
        )
    }

    private fun toggle(value: String, checked: Boolean, action: (Boolean) -> Unit) {
        panel.addView(
            Switch(this).apply {
                text = value
                textSize = 14f
                setTextColor(Color.WHITE)
                isChecked = checked
                isFocusable = true
                setPadding(dp(4), dp(9), dp(4), dp(9))
                setOnCheckedChangeListener { _, v ->
                    action(v)
                    RuntimeEvents.publish(
                        EventLevel.RUNNING,
                        "settings",
                        "Display preference updated: $value",
                    )
                }
            },
            LinearLayout.LayoutParams(-1, dp(48)),
        )
    }

    private fun choose(title: String, values: Array<String>, selected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(values) { _, i ->
                selected(i)
                RuntimeEvents.publish(EventLevel.RUNNING, "settings", "$title preference changed")
                build()
            }
            .show()
    }

    private fun build() {
        panel.removeAllViews()
        text("VISORA / Settings", 26f)
        text("Remote shortcuts: Menu opens controls; Rewind replays only the introduction.", 11f)
        button("Open Casting / AirScreen") {
            CastingController(this).launch()
            finish()
        }
        button("Replay Intro Animation") {
            setResult(RESULT_OK, Intent().putExtra("replay", true))
            finish()
        }
        text(
            "After five minutes idle: black screen and paused graphics. TV power remains unchanged.",
            11f,
        )
        toggle("Play intro automatically on launch", settings.cinematic) { settings.cinematic = it }
        toggle("Show live terminal", settings.terminalEnabled) { settings.terminalEnabled = it }
        button("Terminal typing speed: ${settings.typingSpeed} characters / second") {
            choose("Typing speed", arrayOf("60 cps", "110 cps", "160 cps", "220 cps")) {
                settings.typingSpeed = intArrayOf(60, 110, 160, 220)[it]
            }
        }
        button("Terminal text size: ${settings.terminalSize}") {
            choose(
                "Terminal text size",
                arrayOf("14 / compact", "17 / standard", "20 / larger", "22 / largest"),
            ) {
                settings.terminalSize = intArrayOf(14, 17, 20, 22)[it]
            }
        }
        button("Background intensity: ${settings.intensity}") {
            choose("Background motion", arrayOf("Gentle", "Standard", "Vivid")) {
                settings.intensity = floatArrayOf(.4f, 1f, 1.4f)[it]
            }
        }
        toggle("Reduced motion", settings.reducedMotion) { settings.reducedMotion = it }
        toggle("Show six animated feature icons", settings.showFeatures) {
            settings.showFeatures = it
        }
        button("Performance mode: ${settings.animationQuality}") {
            choose("Animation performance", arrayOf("Auto / adaptive", "Low", "Balanced", "High")) {
                settings.animationQuality = arrayOf("auto", "low", "balanced", "high")[it]
            }
        }
        toggle("24-hour clock", settings.clock24) { settings.clock24 = it }
        text("Screen sizing", 20f)
        toggle("Preserve 16:9 composition", settings.displayFit) { settings.displayFit = it }
        button("Display width: ${settings.displayWidth}%") {
            choose("Display width", arrayOf("70%", "80%", "85%", "90%", "95%", "100%")) {
                settings.displayWidth = intArrayOf(70, 80, 85, 90, 95, 100)[it]
            }
        }
        button("Display height: ${settings.displayHeight}%") {
            choose("Display height", arrayOf("70%", "80%", "85%", "90%", "95%", "100%")) {
                settings.displayHeight = intArrayOf(70, 80, 85, 90, 95, 100)[it]
            }
        }
        text(
            "Sizing applies on return. Reduce dimensions to compensate for television overscan; disable aspect preservation to stretch.",
            11f,
        )
        text("Weather", 20f)
        button("Provider: ${settings.weatherProvider}") {
            choose("Weather provider", arrayOf("Open-Meteo", "AOS Core configured feed")) {
                settings.weatherProvider = arrayOf("open-meteo", "aos-core")[it]
            }
        }
        button("Location: ${settings.weatherLocation.ifBlank { "not configured" }}") {
            searchLocation()
        }
        button("Weather refresh: ${settings.weatherRefreshMinutes} minutes") {
            choose(
                "Weather refresh interval",
                arrayOf("15 minutes", "30 minutes", "60 minutes", "120 minutes"),
            ) {
                settings.weatherRefreshMinutes = intArrayOf(15, 30, 60, 120)[it]
            }
        }
        text(
            "Weather data: Open-Meteo. Expired readings are hidden. Changes apply when you return to the screen.",
            11f,
        )
        toggle("Background monitoring", settings.monitoringEnabled) {
            settings.monitoringEnabled = it
            val intent = Intent(this, ServiceSupervisor::class.java)
            if (it) startForegroundService(intent) else stopService(intent)
        }
        toggle("Monitor AirScreen receiver", settings.monitorCasting) {
            settings.monitorCasting = it
        }
        text(
            "Background recovery never opens external application windows. If permission is required, use the explicit Open Tailscale control.",
            11f,
        )
        text("Network & connections", 20f)
        text(NetworkStatus(this).summary(), 12f)
        text(
            "${BackendConnection.description(settings.token() != null)} · ${if(settings.sshPort == 0) "SSH not configured" else "SSH port ${settings.sshPort}; see live status for last probe"}",
            12f,
        )
        button("Diagnostic intervals / standard or low traffic") {
            choose(
                "Diagnostics schedule",
                arrayOf(
                    "Full cycle / five minutes",
                    "Economy / ten minutes",
                    "Custom milliseconds JSON",
                ),
            ) {
                if (it == 0) settings.diagnosticPolicy = DiagnosticPolicy()
                if (it == 1)
                    settings.diagnosticPolicy =
                        DiagnosticPolicy(
                            600_000,
                            600_000,
                            600_000,
                            600_000,
                            600_000,
                            600_000,
                            600_000,
                            600_000,
                            600_000,
                            300_000,
                        )
                if (it == 2)
                    input(
                        "Diagnostic interval map (milliseconds)",
                        settings.diagnosticPolicy.toJson().toString(),
                        false,
                    ) { value ->
                        runCatching {
                            settings.diagnosticPolicy =
                                DiagnosticPolicy.parse(org.json.JSONObject(value))
                            build()
                        }
                            .onFailure {
                                Toast.makeText(
                                        this,
                                        "Invalid intervals; existing schedule retained",
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                            }
                    }
            }
        }
        button("HTTPS internet probe endpoint") {
            input("HTTPS HEAD endpoint", settings.internetProbe, false) { value ->
                runCatching { settings.internetProbe = value.trim() }
                    .onFailure {
                        Toast.makeText(this, "HTTPS endpoint required", Toast.LENGTH_LONG).show()
                    }
            }
        }
        button("Allow Wi-Fi SSID access (optional location permission)") {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 15)
        }
        button("Device health and animation metrics") {
            AlertDialog.Builder(this)
                .setTitle("Measured device health")
                .setMessage(DeviceHealthMonitor(this).snapshot().toString(2))
                .setPositiveButton("Close", null)
                .show()
        }
        button(if (advanced) "Hide advanced device management" else "Advanced device management") {
            advanced = !advanced
            build()
        }
        if (advanced) buildAdvanced()
        button("Return to unified display") { finish() }
    }

    private fun searchLocation() {
        input("Search city or place", settings.weatherLocation, false) { city ->
            Toast.makeText(this, "Searching weather locations…", Toast.LENGTH_SHORT).show()
            executor.execute {
                val result = runCatching { WeatherManager.search(city) }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    result
                        .onSuccess { places ->
                            if (places.isEmpty())
                                Toast.makeText(
                                        this,
                                        "No matching place; try a nearby town",
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                            else
                                AlertDialog.Builder(this)
                                    .setTitle("Select exact weather location")
                                    .setItems(places.map { it.description }.toTypedArray()) { _, i
                                        ->
                                        val place = places[i]
                                        settings.weatherLatitude = place.latitude
                                        settings.weatherLongitude = place.longitude
                                        settings.weatherLocation = place.name
                                        settings.weatherCache = ""
                                        RuntimeEvents.publish(
                                            EventLevel.RUNNING,
                                            "weather",
                                            "Weather location selected: ${place.name}",
                                        )
                                        build()
                                    }
                                    .show()
                        }
                        .onFailure {
                            Toast.makeText(
                                    this,
                                    "Location lookup unavailable; existing location retained",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                }
            }
        }
    }

    private fun buildAdvanced() {
        toggle("Request VISORA launch after boot", settings.boot) { settings.boot = it }
        toggle("Request Tailscale reconnect if address is absent", settings.reconnect) {
            settings.reconnect = it
        }
        button("Request VPN reconnect") {
            val message = TailscaleMonitor(this).requestReconnect()
            RuntimeEvents.publish(EventLevel.RUNNING, "vpn", message)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        button("Open Tailscale") { CastingController(this).launch("com.tailscale.ipn") }
        button("Install SSH administrator public key") {
            input("One Ed25519 public key (never a private key)", "", false) { value ->
                runCatching { SshController.installPublicKey(this, value) }
                    .onSuccess {
                        stopService(Intent(this, ServiceSupervisor::class.java))
                        if (settings.monitoringEnabled)
                            startForegroundService(Intent(this, ServiceSupervisor::class.java))
                        Toast.makeText(this, "Public key installed", Toast.LENGTH_LONG).show()
                    }
                    .onFailure {
                        Toast.makeText(this, "Invalid Ed25519 public key", Toast.LENGTH_LONG).show()
                    }
            }
        }
        toggle("Enable key-only SSH in VISORA app sandbox", settings.sshEnabled) {
            settings.sshEnabled = it
        }
        text(
            "SSH binds only the actual tailnet address. It has VISORA's UID, not root or ADB privileges. Password login and forwarding are not compiled in.",
            11f,
        )
        button(
            "SSH availability monitoring: ${if(settings.sshPort == 0) "disabled" else settings.sshPort}",
        ) {
            input("SSH port (0 disables; monitoring only)", settings.sshPort.toString(), false) {
                value ->
                runCatching {
                    settings.sshPort = value.toInt()
                    build()
                }
                    .onFailure {
                        Toast.makeText(
                                this,
                                "Use 0 or a port from 1024 to 65535",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    }
            }
        }
        text("AOS Core / enrollment", 20f)
        text(
            if (settings.deviceId.isEmpty())
                "Not enrolled; proposed protocol requires server compatibility"
            else "Device: ${settings.deviceId}",
            12f,
        )
        button("Configure tailnet HTTPS origin") {
            input("AOS Core HTTPS origin", settings.origin, false) { value ->
                runCatching {
                    SecurityPolicy.tailnetEndpoint(value.trim())
                    stopService(Intent(this, ManagementService::class.java))
                    settings.resetRegistration()
                    settings.origin = value.trim()
                    build()
                }
                    .onFailure {
                        Toast.makeText(
                                this,
                                "Use a tailnet HTTPS origin without a path",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    }
            }
        }
        button("Enroll with one-time registration code") {
            if (settings.origin.isEmpty()) {
                Toast.makeText(this, "Set the HTTPS origin first", Toast.LENGTH_LONG).show()
                return@button
            }
            input("One-time code (not stored)", "", true) { code ->
                executor.execute {
                    val result = runCatching { RemoteManagementClient(this).register(code) }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                    this,
                                    if (result.isSuccess) "Enrollment complete"
                                    else "Enrollment unavailable",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                            build()
                        }
                    }
                }
            }
        }
        toggle("Authorize signed remote commands", settings.remoteEnabled) {
            settings.remoteEnabled = it
        }
        button("Application launch allowlist") {
            input("Comma-separated package names", settings.allowedApps.joinToString(","), false) {
                value ->
                runCatching {
                    settings.allowedApps =
                        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    build()
                }
                    .onFailure {
                        Toast.makeText(this, "Invalid package names", Toast.LENGTH_LONG).show()
                    }
            }
        }
        button("Install signer-verified staged update") {
            if (!UpdateManager(this).requestInstall())
                Toast.makeText(this, "No staged update or installer unavailable", Toast.LENGTH_LONG)
                    .show()
        }
        button("Remove enrollment") {
            AlertDialog.Builder(this)
                .setMessage("Remove this device credential and disable remote management?")
                .setPositiveButton("Remove") { _, _ ->
                    settings.resetRegistration()
                    stopService(Intent(this, ManagementService::class.java))
                    build()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun input(title: String, value: String, secret: Boolean, done: (String) -> Unit) {
        val field =
            EditText(this).apply {
                setText(value)
                isSingleLine = true
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD
                        else InputType.TYPE_TEXT_VARIATION_NORMAL
            }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(field)
            .setPositiveButton("Save") { _, _ ->
                done(field.text.toString())
                if (secret) field.text.clear()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
