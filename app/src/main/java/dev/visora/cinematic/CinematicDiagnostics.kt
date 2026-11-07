package dev.visora.cinematic

/** In-process observations for health/tests, never an exported command or listener. */
data class SceneDiagnostics(
    val frames: Long = 0,
    val seconds: Float = 0f,
    val averageIntervalMs: Float = 0f,
    val quality: Int = 1,
    val running: Boolean = false,
    val hardwareAccelerated: Boolean = false,
    val phase: String = "IDLE",
    val ambient: Boolean = false,
    val independentLayers: Int = 0,
    val gpuFrames: Long = 0,
    val gpuFps: Float = 0f,
    val gpuReady: Boolean = false,
    val gpuError: Int = 0,
)

object CinematicDiagnostics {
    @Volatile var snapshot = SceneDiagnostics()
}
