package dev.visora

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayViewportTest {
    @Test
    fun widescreenAndOverscan() {
        assertEquals(1920 to 1080, DisplayViewport.size(1920, 1080, 100, 100, true))
        assertEquals(1728 to 972, DisplayViewport.size(1920, 1080, 90, 100, true))
    }

    @Test
    fun narrowerAndUltrawideWindowsPreserveComposition() {
        assertEquals(1024 to 576, DisplayViewport.size(1024, 768, 100, 100, true))
        assertEquals(2560 to 1440, DisplayViewport.size(3440, 1440, 100, 100, true))
    }

    @Test
    fun independentDimensionsWhenStretchIsSelected() {
        assertEquals(1536 to 1026, DisplayViewport.size(1920, 1080, 80, 95, false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() {
        DisplayViewport.size(0, 1080, 100, 100, true)
    }
}
