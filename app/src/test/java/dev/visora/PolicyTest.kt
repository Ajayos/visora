package dev.visora

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test
    fun tailnetOriginAllowsOnlyPrivateManagementOrigins() {
        listOf("https://core.example.ts.net", "https://100.64.10.20:8443").forEach {
            SecurityPolicy.tailnetEndpoint(it)
        }
        listOf(
                "http://core.example.ts.net",
                "https://example.com",
                "https://user@core.example.ts.net",
                "https://100.128.1.2",
                "https://core.example.ts.net/path",
                "https://core.example.ts.net?token=x",
            )
            .forEach { value ->
                assertThrows(IllegalArgumentException::class.java) {
                    SecurityPolicy.tailnetEndpoint(value)
                }
            }
    }

    @Test
    fun commandsRequireExplicitAuthorizationAndShortExpiry() {
        assertTrue(SecurityPolicy.commandAllowed("restart", 1200, 1000, true))
        assertFalse(SecurityPolicy.commandAllowed("restart", 1200, 1000, false))
        assertFalse(SecurityPolicy.commandAllowed("restart", 1000, 1000, true))
        assertFalse(SecurityPolicy.commandAllowed("restart", 400000, 1000, true))
        assertFalse(SecurityPolicy.commandAllowed("shell", 1200, 1000, true))
    }

    private fun campaign(brand: String = "aos") =
        JSONObject(
            """{"revision":1,"brand":{"id":"aos","name":"AOS","eyebrow":"Independent brand","headline":"Hello","body":"Welcome","accent":"#B9F36A","background":"#080D14"},"slides":[{"id":"one","brand":"$brand","kind":"text","text":"Hello","seconds":10,"start":"2026-11-07T00:00:00Z","end":"2026-11-08T00:00:00Z"}]}""",
        )

    @Test
    fun rejectsCampaignContainingOtherBrand() {
        assertThrows(IllegalArgumentException::class.java) { Campaign.parse(campaign("deluper")) }
    }

    @Test
    fun scheduleUsesStartInclusiveEndExclusive() {
        val c = Campaign.parse(campaign())
        val s = c.slides.single()
        assertTrue(c.active(s.start - 1).isEmpty())
        assertEquals(1, c.active(s.start).size)
        assertTrue(c.active(s.end).isEmpty())
    }

    @Test
    fun rejectsUnsafeMediaAndInvalidDurations() {
        val j = campaign()
        j.getJSONArray("slides").getJSONObject(0).put("seconds", 0)
        assertThrows(IllegalArgumentException::class.java) { Campaign.parse(j) }
        val media = campaign()
        media
            .getJSONArray("slides")
            .getJSONObject(0)
            .put("kind", "video")
            .put("sha256", "../escape")
            .put("url", "http://host/a")
        assertThrows(IllegalArgumentException::class.java) { Campaign.parse(media) }
    }

    @Test
    fun checksumIsStable() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SecurityPolicy.hash("abc".toByteArray()),
        )
    }
}
