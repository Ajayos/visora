package dev.visora

import dev.visora.cinematic.LogoMatte
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun eachCheckRunsOnceAtItsDeadlineAndDoesNotDuplicateOnResume() {
        val schedule = DiagnosticSchedule(DiagnosticPolicy())
        assertTrue(schedule.due("memory", 100))
        assertFalse(schedule.due("memory", 101))
        assertFalse(schedule.due("memory", 300099))
        assertTrue(schedule.due("memory", 300100))
        assertTrue(schedule.due("storage", 300100))
        assertFalse(schedule.due("storage", 300100))
    }

    @Test
    fun serviceRecoveryIsFiniteAndBacksOffUntilObservedRecovery() {
        val gate = RecoveryGate()
        assertTrue(gate.due(100))
        gate.requested(100)
        assertFalse(gate.due(30099))
        assertTrue(gate.due(30100))
        gate.requested(30100)
        assertFalse(gate.due(90099))
        gate.requested(90100)
        assertFalse(gate.due(9999999))
        gate.reset()
        assertTrue(gate.due(9999999))
    }

    @Test
    fun networkAndSyncIntervalsCannotBeConfiguredAsBusyLoops() {
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticPolicy.parse(JSONObject("""{"latency":1}"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticPolicy.parse(JSONObject("""{"sync":1000}"""))
        }
        assertEquals(300000L, DiagnosticPolicy.parse(null).sync)
    }

    @Test
    fun cpuReportsMeasuredDeltasAndNeverInventsGlobalUsage() {
        val cpu = CpuSampler()
        val first = cpu.sample(1000, 100, null)
        assertNull(first.processPercent)
        assertNull(first.systemPercent)
        val second = cpu.sample(21000, 2100, null)
        assertEquals(10.0, second.processPercent!!, .001)
        assertNull(second.systemPercent)
        val global = CpuSampler()
        global.sample(1000, 10, "cpu 100 0 50 850 0 0 0 0")
        val sample = global.sample(2000, 15, "cpu 150 0 100 950 0 0 0 0")
        assertEquals(50.0, sample.systemPercent!!, .001)
    }

    @Test
    fun matteRemovesTinyStarsAndThinTrailsWithoutChangingLogoColors() {
        val width = 80
        val height = 50
        val pixels = IntArray(width * height) { 0xFF15124A.toInt() }
        val color = 0xFFF1CBFF.toInt()
        for (y in 12..38) for (x in 20..55) pixels[y * width + x] = color
        for (x in 0 until width) pixels[3 * width + x] = 0xFFFFFFFF.toInt()
        pixels[7 * width + 70] = 0xFFFFFFFF.toInt()
        val alpha = LogoMatte.extract(pixels, width, height, false)
        assertEquals(0, alpha[7 * width + 70])
        assertEquals(0, alpha[3 * width + 30])
        assertEquals(color, alpha[24 * width + 30])
        assertEquals(0, alpha[0])
    }
}
