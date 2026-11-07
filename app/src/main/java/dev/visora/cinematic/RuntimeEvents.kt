package dev.visora.cinematic

import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Real app events only. Replay never clears or fabricates history. Messages must never contain
 * secrets.
 */
data class RuntimeEvent(
    val sequence: Long,
    val timestamp: Long,
    val level: EventLevel,
    val source: String,
    val message: String,
)

object RuntimeEvents {
    private val history = ArrayDeque<RuntimeEvent>()
    private val listeners = CopyOnWriteArraySet<(RuntimeEvent) -> Unit>()
    private var sequence = 0L

    @Synchronized
    fun publish(level: EventLevel, source: String, message: String) {
        val event =
            synchronized(this) {
                RuntimeEvent(
                        ++sequence,
                        System.currentTimeMillis(),
                        level,
                        source.take(24),
                        message.take(180).replace('\n', ' '),
                    )
                    .also {
                        history.add(it)
                        while (history.size > 120) history.removeFirst()
                    }
            }
        listeners.forEach { it(event) }
    }

    @Synchronized fun snapshot(): List<RuntimeEvent> = history.toList()

    fun subscribe(listener: (RuntimeEvent) -> Unit) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: (RuntimeEvent) -> Unit) {
        listeners.remove(listener)
    }
}

object BackendConnection {
    @Volatile private var reachedAt = 0L
    @Volatile private var failedAt = 0L

    fun reached() {
        reachedAt = System.currentTimeMillis()
    }

    fun failed() {
        failedAt = System.currentTimeMillis()
    }

    fun description(enrolled: Boolean, now: Long = System.currentTimeMillis()): String =
        when {
            !enrolled -> "CORE NOT ENROLLED"
            failedAt >= reachedAt && failedAt != 0L -> "CORE UNREACHABLE"
            reachedAt == 0L -> "CORE NOT CHECKED"
            now - reachedAt > 360_000 -> "CORE STATUS STALE"
            else -> "CORE REACHED"
        }
}
