package dev.visora.cinematic

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.visora.*
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt
import org.json.JSONObject

data class WeatherState(
    val snapshot: WeatherSnapshot? = null,
    val label: String = "SET WEATHER LOCATION",
)

data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
)

/**
 * Explicit configured provider; no IP geolocation, hardcoded city, or weather API credential in the
 * APK.
 */
class WeatherManager(private val context: Context, private val update: (WeatherState) -> Unit) {
    private val settings = SettingsManager(context)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var future: ScheduledFuture<*>? = null
    @Volatile private var visible = false
    @Volatile private var connection: HttpsURLConnection? = null
    private var nextRequest = 0L
    private var selectedLocation = ""

    private fun publish(state: WeatherState) {
        handler.post { if (visible) update(state) }
    }

    fun start() {
        visible = true
        future?.cancel(false)
        future = executor.scheduleWithFixedDelay({ refresh() }, 0, 60, TimeUnit.SECONDS)
    }

    fun pause() {
        visible = false
        future?.cancel(true)
        future = null
        connection?.disconnect()
        handler.removeCallbacksAndMessages(null)
    }

    fun close() {
        pause()
        executor.shutdownNow()
    }

    private fun refresh() {
        if (!visible) return
        if (settings.weatherProvider == "aos-core") {
            val cached = (context.applicationContext as VisoraApp).engine.campaign?.weather
            publish(
                WeatherState(
                    cached?.takeIf { it.fresh(System.currentTimeMillis()) },
                    if (cached?.fresh(System.currentTimeMillis()) == true) "AOS CORE WEATHER"
                    else "WEATHER UNAVAILABLE",
                ),
            )
            return
        }
        val city = settings.weatherLocation
        if (city.isBlank()) {
            publish(WeatherState())
            return
        }
        val now = System.currentTimeMillis()
        val locationKey =
            "$city/${settings.weatherLatitude}/${settings.weatherLongitude}/${settings.weatherRefreshMinutes}"
        if (selectedLocation != locationKey) {
            selectedLocation = locationKey
            nextRequest = 0
        }
        val cached = runCatching {
            WeatherSnapshot.parse(JSONObject(settings.weatherCache))
        }
            .getOrNull()
            ?.takeIf { it.location == city && it.fresh(now) }
        if (now < nextRequest) {
            publish(
                WeatherState(cached, if (cached != null) "OPEN-METEO" else "WEATHER UNAVAILABLE"),
            )
            return
        }
        if (!NetworkMonitor(context).available()) {
            publish(
                WeatherState(cached, if (cached != null) "CACHED / OFFLINE" else "WEATHER OFFLINE"),
            )
            nextRequest = now + 60_000
            return
        }
        publish(WeatherState(cached, "WEATHER LOADING"))
        try {
            val latitude = settings.weatherLatitude
            val longitude = settings.weatherLongitude
            val raw =
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&timeformat=unixtime",
                )
            val current = raw.getJSONObject("current")
            require(raw.getJSONObject("current_units").getString("temperature_2m") == "°C")
            val observedAt = current.getLong("time") * 1000L
            val nowAfter = System.currentTimeMillis()
            require(observedAt <= nowAfter + 300_000 && nowAfter - observedAt < 2 * 60 * 60 * 1000)
            val expiry = observedAt + 2 * 60 * 60 * 1000L
            val snapshot =
                WeatherSnapshot.parse(
                    JSONObject()
                        .put("celsius", current.getDouble("temperature_2m").roundToInt())
                        .put("location", city)
                        .put("condition", condition(current.getInt("weather_code")))
                        .put("observedAt", observedAt)
                        .put("expiresAt", expiry),
                )!!
            if (city != settings.weatherLocation || !visible) return
            settings.weatherCache =
                JSONObject()
                    .put("celsius", snapshot.celsius)
                    .put("location", city)
                    .put("condition", snapshot.condition)
                    .put("observedAt", observedAt)
                    .put("expiresAt", expiry)
                    .toString()
            nextRequest = nowAfter + settings.weatherRefreshMinutes * 60_000L
            publish(WeatherState(snapshot, "OPEN-METEO"))
            RuntimeEvents.publish(EventLevel.OK, "weather", "Current weather refreshed for $city")
        } catch (_: Exception) {
            if (!visible) {
                nextRequest = 0L
                return
            }
            nextRequest = System.currentTimeMillis() + 5 * 60_000
            publish(
                WeatherState(
                    cached,
                    if (cached != null) "CACHED / UPDATE FAILED" else "WEATHER UNAVAILABLE",
                ),
            )
            if (visible)
                RuntimeEvents.publish(
                    EventLevel.WARNING,
                    "weather",
                    "Provider unavailable; bounded retry in five minutes",
                )
        }
    }

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpsURLConnection
        connection = c
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.instanceFollowRedirects = false
            check(c.responseCode == 200)
            return JSONObject(
                String(c.inputStream.use { it.readBytesLimited(256 * 1024) }, Charsets.UTF_8),
            )
        } finally {
            connection = null
            c.disconnect()
        }
    }

    companion object {
        fun condition(code: Int): String =
            when (code) {
                0,
                1 -> "clear"
                2,
                3 -> "cloud"
                45,
                48 -> "fog"
                in 51..67,
                in 80..82 -> "rain"
                in 71..77,
                85,
                86 -> "snow"
                in 95..99 -> "storm"
                else -> "unknown"
            }

        fun search(city: String): List<WeatherLocation> {
            require(city.trim().length in 2..80)
            val url =
                "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city.trim(),"UTF-8")}&count=5&language=en&format=json"
            val c = URL(url).openConnection() as HttpsURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.instanceFollowRedirects = false
                check(c.responseCode == 200)
                val j =
                    JSONObject(
                        String(
                            c.inputStream.use { it.readBytesLimited(128 * 1024) },
                            Charsets.UTF_8,
                        ),
                    )
                val results = j.optJSONArray("results") ?: return emptyList()
                return (0 until results.length()).map { i ->
                    val r = results.getJSONObject(i)
                    WeatherLocation(
                        r.getString("name").take(40),
                        r.getDouble("latitude"),
                        r.getDouble("longitude"),
                        "${r.getString("name")}, ${r.optString("admin1")}, ${r.optString("country")}",
                    )
                }
            } finally {
                c.disconnect()
            }
        }
    }
}
