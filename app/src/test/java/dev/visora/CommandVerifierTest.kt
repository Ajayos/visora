package dev.visora

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommandVerifierTest {
    private val keys =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val key
        get() = Base64.getEncoder().encodeToString(keys.public.encoded)

    private fun envelope(payload: String): JSONObject {
        val bytes = payload.toByteArray()
        val signature =
            Signature.getInstance("SHA256withRSA")
                .apply {
                    initSign(keys.private)
                    update(bytes)
                }
                .sign()
        return JSONObject()
            .put("payload", Base64.getEncoder().encodeToString(bytes))
            .put("signature", Base64.getEncoder().encodeToString(signature))
    }

    @Test
    fun verifiesActualSignature() {
        assertEquals(
            "tv_1",
            CommandVerifier.verify(envelope("""{"deviceId":"tv_1","sequence":1}"""), key, "tv_1")
                .getString("deviceId"),
        )
    }

    @Test
    fun rejectsTamperedPayload() {
        val e = envelope("""{"deviceId":"tv_1"}""")
        e.put(
            "payload",
            Base64.getEncoder().encodeToString("""{"deviceId":"tv_2"}""".toByteArray()),
        )
        assertThrows(IllegalArgumentException::class.java) {
            CommandVerifier.verify(e, key, "tv_2")
        }
    }

    @Test
    fun rejectsAnotherDeviceEvenWithValidSignature() {
        assertThrows(IllegalArgumentException::class.java) {
            CommandVerifier.verify(envelope("""{"deviceId":"tv_2"}"""), key, "tv_1")
        }
    }

    @Test
    fun rejectsAnotherSigningKey() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertThrows(IllegalArgumentException::class.java) {
            CommandVerifier.verify(
                envelope("""{"deviceId":"tv_1"}"""),
                Base64.getEncoder().encodeToString(other.public.encoded),
                "tv_1",
            )
        }
    }
}
