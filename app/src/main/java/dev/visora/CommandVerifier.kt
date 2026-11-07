package dev.visora

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

object CommandVerifier {
    fun verify(envelope: JSONObject, encodedKey: String, deviceId: String): JSONObject {
        val encoded = envelope.getString("payload")
        require(encoded.length <= 44 * 1024)
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size <= 32 * 1024)
        val key =
            KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)))
        require((key as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 2048)
        val verifier =
            Signature.getInstance("SHA256withRSA").apply {
                initVerify(key)
                update(bytes)
            }
        require(verifier.verify(Base64.getDecoder().decode(envelope.getString("signature")))) {
            "Invalid command signature"
        }
        return JSONObject(String(bytes, Charsets.UTF_8)).also {
            require(it.getString("deviceId") == deviceId)
        }
    }
}
