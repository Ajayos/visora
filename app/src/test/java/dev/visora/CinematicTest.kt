package dev.visora

import dev.visora.cinematic.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CinematicTest {
    @Test
    fun progressCannotFinishUntilEveryRealStepResolves() {
        val state = StartupStateMachine()
        for ((i, step) in StartupStep.entries.withIndex()) {
            state.accept(StartupEvent(step, EventLevel.RUNNING, "working", 0))
            assertEquals(i * 100 / StartupStep.entries.size, state.progress)
            assertFalse(SceneTimeline.canEnterAmbient(300f, AnimationConfig(), state.phase, true))
            state.accept(
                StartupEvent(
                    step,
                    if (step == StartupStep.CORE) EventLevel.WARNING else EventLevel.OK,
                    "actual result",
                    0,
                ),
            )
        }
        assertEquals(100, state.progress)
        assertEquals(StartupPhase.DEGRADED, state.phase)
        assertTrue(SceneTimeline.canEnterAmbient(20f, AnimationConfig(), state.phase, true))
        assertFalse(SceneTimeline.canEnterAmbient(20f, AnimationConfig(), state.phase, false))
    }

    @Test
    fun incompleteAndCancelledWorkCannotAppearReady() {
        val state = StartupStateMachine()
        state.accept(StartupEvent(StartupStep.RUNTIME, EventLevel.RUNNING, "starting", 0))
        state.cancel()
        assertEquals(0, state.progress)
        assertFalse(SceneTimeline.canEnterAmbient(500f, AnimationConfig(), state.phase, true))
        assertThrows(IllegalStateException::class.java) {
            state.accept(StartupEvent(StartupStep.RUNTIME, EventLevel.OK, "late result", 0))
        }
    }

    @Test
    fun phasesCannotSkipOrOverwriteActualResults() {
        val state = StartupStateMachine()
        assertThrows(IllegalArgumentException::class.java) {
            state.accept(StartupEvent(StartupStep.CORE, EventLevel.OK, "fake", 0))
        }
        state.accept(StartupEvent(StartupStep.RUNTIME, EventLevel.RUNNING, "working", 0))
        state.accept(StartupEvent(StartupStep.RUNTIME, EventLevel.ERROR, "failed", 0))
        assertThrows(IllegalArgumentException::class.java) {
            state.accept(StartupEvent(StartupStep.RUNTIME, EventLevel.OK, "overwrite", 0))
        }
        assertTrue(state.hasErrors)
    }

    @Test
    fun animationConfigurationEnforcesDeviceBudgets() {
        listOf(
                """{"stars":99999}""",
                """{"fps":120}""",
                """{"introSeconds":0}""",
                """{"motion":99}""",
                """{"videoBackground":"../escape"}""",
                """{"quality":"ultra"}""",
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) {
                    AnimationConfig.parse(JSONObject(it))
                }
            }
        assertEquals(24, AnimationConfig.parse(JSONObject("""{"fps":24,"stars":2200}""")).fps)
    }

    @Test
    fun promptConfigurationCannotInventStartupTasks() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationConfig.parse(JSONObject("""{"prompts":{"shell":"Root shell connected"}}"""))
        }
        assertEquals(
            "Inspect local cache",
            AnimationConfig.parse(JSONObject("""{"prompts":{"content":"Inspect local cache"}}"""))
                .prompts["content"],
        )
    }

    @Test
    fun weatherRequiresValidExpiringDataAndIsNeverInvented() {
        assertNull(WeatherSnapshot.parse(null))
        val w =
            WeatherSnapshot.parse(
                JSONObject(
                    """{"celsius":28,"location":"Test city","condition":"cloud","observedAt":1000,"expiresAt":2000}""",
                ),
            )!!
        assertFalse(w.fresh(999))
        assertTrue(w.fresh(1000))
        assertFalse(w.fresh(2000))
        assertThrows(IllegalArgumentException::class.java) {
            WeatherSnapshot.parse(
                JSONObject(
                    """{"celsius":28,"location":"Test","condition":"cloud","observedAt":1000,"expiresAt":999999999}""",
                ),
            )
        }
    }

    @Test
    fun revealHasExactSmoothEndpoints() {
        assertEquals(0f, SceneTimeline.reveal(1f, 2f, 2f), 0f)
        assertEquals(0f, SceneTimeline.reveal(2f, 2f, 2f), 0f)
        assertEquals(.5f, SceneTimeline.reveal(3f, 2f, 2f), .001f)
        assertEquals(1f, SceneTimeline.reveal(4f, 2f, 2f), 0f)
        assertEquals(1f, SceneTimeline.reveal(100f, 2f, 2f), 0f)
    }
}
