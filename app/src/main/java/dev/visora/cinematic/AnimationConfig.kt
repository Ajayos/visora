package dev.visora.cinematic

import org.json.JSONObject

/** Bounded knobs; server configuration can never request unbounded particle counts or FPS. */
data class AnimationConfig(
    val enabled: Boolean = true,
    val quality: String = "auto",
    val stars: Int = 1800,
    val fps: Int = 30,
    val motion: Float = 1f,
    val introSeconds: Int = 12,
    val typingCps: Int = 100,
    val terminal: Boolean = true,
    val features: Boolean = true,
    val videoBackground: String = "",
    val prompts: Map<String, String> = emptyMap(),
) {
    companion object {
        fun parse(json: JSONObject?): AnimationConfig {
            if (json == null) return AnimationConfig()
            val quality = json.optString("quality", "auto")
            require(quality in setOf("auto", "low", "balanced", "high"))
            val stars = json.optInt("stars", 1800)
            require(stars in 400..2600)
            val fps = json.optInt("fps", 30)
            require(fps in setOf(20, 24, 30))
            val motion = json.optDouble("motion", 1.0).toFloat()
            require(motion.isFinite() && motion in .1f..1.5f)
            val intro = json.optInt("introSeconds", 12)
            require(intro in 8..30)
            val cps = json.optInt("typingCps", 100)
            require(cps in 30..240)
            val video = json.optString("videoBackground")
            require(video.isEmpty() || Regex("[a-f0-9]{64}").matches(video))
            val prompts = mutableMapOf<String, String>()
            json.optJSONObject("prompts")?.let { p ->
                require(p.length() <= StartupStep.entries.size)
                p.keys().forEach { k ->
                    require(StartupStep.entries.any { it.id == k })
                    val value = p.getString(k)
                    require(value.length in 1..64 && value.none { it.isISOControl() })
                    prompts[k] = value
                }
            }
            return AnimationConfig(
                json.optBoolean("enabled", true),
                quality,
                stars,
                fps,
                motion,
                intro,
                cps,
                json.optBoolean("terminal", true),
                json.optBoolean("features", true),
                video,
                prompts,
            )
        }
    }
}

data class WeatherSnapshot(
    val celsius: Int,
    val location: String,
    val condition: String,
    val observedAt: Long,
    val expiresAt: Long,
) {
    fun fresh(now: Long) = now in observedAt until expiresAt

    companion object {
        fun parse(j: JSONObject?): WeatherSnapshot? {
            if (j == null) return null
            val c = j.getInt("celsius")
            require(c in -80..70)
            val condition = j.getString("condition")
            require(condition in setOf("clear", "cloud", "rain", "storm", "snow", "fog", "unknown"))
            val location = j.getString("location")
            require(location.length in 1..40 && location.none { it.isISOControl() })
            val observed = j.getLong("observedAt")
            val expires = j.getLong("expiresAt")
            require(expires > observed && expires - observed <= 6 * 60 * 60 * 1000L)
            return WeatherSnapshot(c, location, condition, observed, expires)
        }
    }
}

enum class StartupStep(val id: String, val prompt: String) {
    RUNTIME("runtime", "Initializing VISORA runtime"),
    VISUALS("visuals", "Loading independent visual layers"),
    SERVICES("services", "Inspecting device services"),
    NETWORK("network", "Checking network connectivity"),
    VPN("vpn", "Observing Tailscale connectivity"),
    CORE("core", "Checking authenticated AOS Core connection"),
    CONTENT("content", "Validating cached promotional content"),
    DISPLAY("display", "Launching configured display"),
}

enum class EventLevel {
    RUNNING,
    OK,
    WARNING,
    ERROR,
}

data class StartupEvent(
    val step: StartupStep,
    val level: EventLevel,
    val message: String,
    val elapsedMs: Long,
)

enum class StartupPhase {
    IDLE,
    RUNNING,
    READY,
    DEGRADED,
    CANCELLED,
}

/** Task progress is derived only from terminal task states, never animation time. */
class StartupStateMachine {
    private val states = linkedMapOf<StartupStep, EventLevel>()
    var phase = StartupPhase.IDLE
        private set

    val completed
        get() = states.values.count { it != EventLevel.RUNNING }

    val progress
        get() = completed * 100 / StartupStep.entries.size

    val hasErrors
        get() = states.values.any { it == EventLevel.ERROR }

    fun accept(event: StartupEvent) {
        check(
            phase != StartupPhase.CANCELLED &&
                phase !in setOf(StartupPhase.READY, StartupPhase.DEGRADED),
        )
        val previous = states[event.step]
        require(previous != null || event.level == EventLevel.RUNNING) {
            "A task must begin before it can finish"
        }
        require(previous == null || previous == EventLevel.RUNNING) {
            "Completed task is immutable"
        }
        require(previous != null || states.values.none { it == EventLevel.RUNNING }) {
            "Startup steps are serial"
        }
        require(previous != null || event.step == StartupStep.entries.getOrNull(states.size)) {
            "Startup steps must stay ordered"
        }
        states[event.step] = event.level
        phase =
            if (completed == StartupStep.entries.size) {
                if (states.values.any { it in setOf(EventLevel.WARNING, EventLevel.ERROR) })
                    StartupPhase.DEGRADED
                else StartupPhase.READY
            } else StartupPhase.RUNNING
    }

    fun cancel() {
        if (phase !in setOf(StartupPhase.READY, StartupPhase.DEGRADED))
            phase = StartupPhase.CANCELLED
    }
}

/** Stable phase offsets and bounded motion avoid discontinuities at loop boundaries. */
object SceneTimeline {
    fun reveal(seconds: Float, start: Float, duration: Float): Float {
        val f = ((seconds - start) / duration).coerceIn(0f, 1f)
        return f * f * (3f - 2f * f)
    }

    fun canEnterAmbient(
        elapsed: Float,
        config: AnimationConfig,
        phase: StartupPhase,
        consoleDrained: Boolean,
    ): Boolean =
        elapsed >= config.introSeconds &&
            phase in setOf(StartupPhase.READY, StartupPhase.DEGRADED) &&
            (consoleDrained || !config.terminal)
}
