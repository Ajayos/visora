package dev.visora

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.Base64

object SshKeyPolicy {
    /** Only one plain Ed25519 public key. No authorized_keys options/commands supplied by input. */
    fun canonical(text: String): String {
        require(text.length <= 1024 && !text.trim().contains('\n') && !text.contains('\r'))
        val fields = text.trim().split(Regex("\\s+"))
        require(fields.size >= 2 && fields[0] == "ssh-ed25519") { "Use an Ed25519 public key" }
        val bytes = Base64.getDecoder().decode(fields[1])
        require(bytes.size == 51)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 11)
            val name = ByteArray(11)
            input.readFully(name)
            require(String(name, Charsets.US_ASCII) == "ssh-ed25519")
            require(input.readInt() == 32 && input.available() == 32)
        }
        return "ssh-ed25519 ${Base64.getEncoder().encodeToString(bytes)} VISORA administrator"
    }
}
