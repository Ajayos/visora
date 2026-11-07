package dev.visora

import dev.visora.cinematic.*
import org.junit.Assert.*
import org.junit.Test

class LiveMonitorTest {
    @Test
    fun boundedHistoryKeepsRealTimestampsAndReplayDoesNotEraseIt() {
        val start = System.currentTimeMillis()
        for (i in 0..150) RuntimeEvents.publish(
            EventLevel.RUNNING,
            "test",
            "Observed test event $i",
        )
        val history = RuntimeEvents.snapshot()
        assertEquals(120, history.size)
        assertTrue(
            history.all { it.timestamp >= start && it.timestamp <= System.currentTimeMillis() },
        )
        assertTrue(history.zipWithNext().all { (a, b) -> b.sequence > a.sequence })
        val sequence = history.last().sequence
        RuntimeEvents.publish(EventLevel.RUNNING, "display", "Visual replay requested")
        assertTrue(RuntimeEvents.snapshot().any { it.sequence == sequence })
    }

    @Test
    fun unconfiguredBackendCannotAppearConnected() {
        BackendConnection.reached()
        assertEquals("CORE NOT ENROLLED", BackendConnection.description(false))
        BackendConnection.failed()
        assertEquals("CORE UNREACHABLE", BackendConnection.description(true))
    }

    @Test
    fun weatherCodesDoNotInventRainForUnknownOrSnow() {
        assertEquals("snow", WeatherManager.condition(75))
        assertEquals("fog", WeatherManager.condition(45))
        assertEquals("unknown", WeatherManager.condition(999))
        assertEquals("storm", WeatherManager.condition(95))
    }
}
