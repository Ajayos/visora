package dev.visora

import dev.visora.cinematic.AnimationConfig
import dev.visora.cinematic.WeatherSnapshot
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

enum class DisplayMode {
    PROMOTION,
    CASTING,
}

data class Brand(
    val id: String,
    val name: String,
    val eyebrow: String,
    val headline: String,
    val body: String,
    val accent: String,
    val background: String,
    val logo: String = "",
)

data class Slide(
    val id: String,
    val brand: String,
    val kind: String,
    val text: String,
    val body: String,
    val sha256: String,
    val url: String,
    val seconds: Int,
    val start: Long,
    val end: Long,
)

data class Campaign(
    val revision: Long,
    val brand: Brand,
    val theme: String,
    val layout: String,
    val slides: List<Slide>,
    val animation: AnimationConfig = AnimationConfig(),
    val weather: WeatherSnapshot? = null,
) {
    fun active(now: Long) = slides.filter { now >= it.start && now < it.end }

    companion object {
        fun parse(json: JSONObject): Campaign {
            val b = json.getJSONObject("brand")
            val id = b.getString("id")
            require(id in setOf("aos", "deluper"))
            fun color(key: String): String =
                b.getString(key).also { require(Regex("#[0-9a-fA-F]{6}").matches(it)) }
            val brand =
                Brand(
                    id,
                    b.getString("name").take(80),
                    b.getString("eyebrow").take(120),
                    b.getString("headline").take(200),
                    b.getString("body").take(500),
                    color("accent"),
                    color("background"),
                    b.optString("logo"),
                )
            require(brand.logo.isEmpty() || Regex("[a-f0-9]{64}").matches(brand.logo))
            val array = json.getJSONArray("slides")
            require(array.length() <= 100)
            val slides =
                (0 until array.length()).map { i ->
                    val s = array.getJSONObject(i)
                    require(s.getString("brand") == id) { "Mixed-brand campaigns are forbidden" }
                    val kind = s.getString("kind")
                    require(kind in setOf("text", "image", "video"))
                    val hash = s.optString("sha256")
                    val url = s.optString("url")
                    if (kind != "text") {
                        require(Regex("[a-f0-9]{64}").matches(hash))
                        SecurityPolicy.https(url)
                    }
                    val start =
                        if (s.has("start")) Instant.parse(s.getString("start")).toEpochMilli()
                        else 0L
                    val end =
                        if (s.has("end")) Instant.parse(s.getString("end")).toEpochMilli()
                        else Long.MAX_VALUE
                    require(end > start)
                    Slide(
                        s.getString("id").take(80),
                        id,
                        kind,
                        s.optString("text").take(300),
                        s.optString("body").take(700),
                        hash,
                        url,
                        s.optInt("seconds", 15).also { require(it in 5..300) },
                        start,
                        end,
                    )
                }
            val theme = json.optString("theme", "midnight")
            require(theme in setOf("midnight", "warm", "ink"))
            val layout = json.optString("layout", "editorial")
            require(layout in setOf("editorial", "centered", "fullscreen"))
            val animation = AnimationConfig.parse(json.optJSONObject("animation"))
            require(
                animation.videoBackground.isEmpty() ||
                    slides.any { it.kind == "video" && it.sha256 == animation.videoBackground },
            ) {
                "Video background must reference a declared cached video"
            }
            return Campaign(
                json.getLong("revision").also { require(it > 0) },
                brand,
                theme,
                layout,
                slides,
                animation,
                WeatherSnapshot.parse(json.optJSONObject("weather")),
            )
        }
    }
}

object SecurityPolicy {
    fun https(value: String): URI =
        URI(value).also {
            require(
                it.scheme == "https" &&
                    !it.host.isNullOrBlank() &&
                    it.userInfo == null &&
                    it.fragment == null,
            ) {
                "HTTPS endpoint required"
            }
        }

    fun tailnetEndpoint(value: String): URI =
        https(value).also {
            val host = it.host.lowercase()
            val octets = host.split('.').mapNotNull { part -> part.toIntOrNull() }
            require(
                host.endsWith(".ts.net") ||
                    (octets.size == 4 &&
                        octets[0] == 100 &&
                        octets[1] in 64..127 &&
                        octets.all { n -> n in 0..255 }),
            ) {
                "Use a tailnet .ts.net name or 100.64.0.0/10 address"
            }
            require(it.query == null && it.path in listOf("", "/")) {
                "Use an origin, without a path"
            }
        }

    fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun commandAllowed(type: String, expires: Long, now: Long, enabled: Boolean): Boolean =
        enabled &&
            expires > now &&
            expires <= now + 300_000 &&
            type in setOf("set_mode", "restart", "launch_app", "stage_update")
}
