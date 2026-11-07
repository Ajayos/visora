package dev.visora

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SshKeyPolicyTest {
    private fun key(length: Int = 32): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(11)
            it.writeBytes("ssh-ed25519")
            it.writeInt(length)
            it.write(ByteArray(length) { n -> (n + 1).toByte() })
        }
        return "ssh-ed25519 ${Base64.getEncoder().encodeToString(bytes.toByteArray())} administrator"
    }

    @Test
    fun acceptsOneStructuredPublicKeyAndCanonicalizesComment() {
        assertTrue(SshKeyPolicy.canonical(key()).endsWith("VISORA administrator"))
    }

    @Test
    fun rejectsPrivateKeyAndAuthorizedKeyCommandOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            SshKeyPolicy.canonical("-----BEGIN OPENSSH PRIVATE KEY-----")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SshKeyPolicy.canonical("command=evil " + key())
        }
    }

    @Test
    fun rejectsMalformedWireKeyAndMultipleKeys() {
        assertThrows(IllegalArgumentException::class.java) { SshKeyPolicy.canonical(key(31)) }
        assertThrows(IllegalArgumentException::class.java) {
            SshKeyPolicy.canonical(key() + "\n" + key())
        }
    }
}
