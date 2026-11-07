package dev.visora

/**
 * Each failure episode has a finite budget. Only an observed recovery/network transition resets it.
 */
class RecoveryGate(private val limit: Int = 3) {
    var attempts = 0
        private set

    private var nextAt = 0L

    fun due(now: Long): Boolean = attempts < limit && now >= nextAt

    fun requested(now: Long) {
        attempts++
        nextAt = now + (30_000L shl (attempts - 1).coerceAtMost(4)).coerceAtMost(300_000L)
    }

    fun reset() {
        attempts = 0
        nextAt = 0
    }
}
