package dev.visora

import org.json.JSONObject

data class DiagnosticPolicy(
    val memory: Long = 300_000,
    val cpu: Long = 300_000,
    val wifi: Long = 300_000,
    val latency: Long = 300_000,
    val vpn: Long = 300_000,
    val ssh: Long = 300_000,
    val backend: Long = 300_000,
    val storage: Long = 300_000,
    val health: Long = 300_000,
    val sync: Long = 300_000,
) {
    fun toJson() =
        JSONObject()
            .put("memory", memory)
            .put("cpu", cpu)
            .put("wifi", wifi)
            .put("latency", latency)
            .put("vpn", vpn)
            .put("ssh", ssh)
            .put("backend", backend)
            .put("storage", storage)
            .put("health", health)
            .put("sync", sync)

    companion object {
        fun parse(j: JSONObject?): DiagnosticPolicy {
            if (j == null) return DiagnosticPolicy()
            fun interval(key: String, default: Long, minimum: Long = 10_000): Long =
                j.optLong(key, default).also {
                    require(it in minimum..900_000) { "Diagnostic interval out of bounds" }
                }
            return DiagnosticPolicy(
                interval("memory", 300_000),
                interval("cpu", 300_000),
                interval("wifi", 300_000),
                interval("latency", 300_000, 30_000),
                interval("vpn", 300_000),
                interval("ssh", 300_000, 30_000),
                interval("backend", 300_000, 30_000),
                interval("storage", 300_000, 30_000),
                interval("health", 300_000),
                interval("sync", 300_000, 60_000),
            )
        }
    }
}

/**
 * Deadlines advance at actual task dispatch. One scheduler owns all checks; no duplicate timers on
 * resume.
 */
class DiagnosticSchedule(private val policy: DiagnosticPolicy) {
    private val deadlines = mutableMapOf<String, Long>()
    private val intervals =
        mapOf(
            "memory" to policy.memory,
            "cpu" to policy.cpu,
            "wifi" to policy.wifi,
            "latency" to policy.latency,
            "vpn" to policy.vpn,
            "ssh" to policy.ssh,
            "backend" to policy.backend,
            "storage" to policy.storage,
            "health" to policy.health,
        )

    fun due(key: String, now: Long): Boolean {
        val interval = requireNotNull(intervals[key])
        if (now < deadlines.getOrDefault(key, 0L)) return false
        deadlines[key] = now + interval
        return true
    }
}
