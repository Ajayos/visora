package dev.visora

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("visora", Context.MODE_PRIVATE)
    var displayWidth: Int
        get() = prefs.getInt("displayWidth", 100)
        set(v) {
            require(v in 70..100)
            prefs.edit().putInt("displayWidth", v).apply()
        }

    var displayHeight: Int
        get() = prefs.getInt("displayHeight", 100)
        set(v) {
            require(v in 70..100)
            prefs.edit().putInt("displayHeight", v).apply()
        }

    var displayFit: Boolean
        get() = prefs.getBoolean("displayFit", true)
        set(v) {
            prefs.edit().putBoolean("displayFit", v).apply()
        }

    var brand: String
        get() = prefs.getString("brand", "aos")!!
        set(v) {
            require(v in setOf("aos", "deluper"))
            prefs.edit().putString("brand", v).commit()
        }

    var mode: DisplayMode
        get() = DisplayMode.valueOf(prefs.getString("mode", "PROMOTION")!!)
        set(v) {
            prefs.edit().putString("mode", v.name).commit()
        }

    var boot: Boolean
        get() = prefs.getBoolean("boot", true)
        set(v) {
            prefs.edit().putBoolean("boot", v).commit()
        }

    var dedicatedBoot: Boolean
        get() = prefs.getBoolean("dedicatedBoot", true)
        set(v) {
            prefs.edit().putBoolean("dedicatedBoot", v).commit()
        }

    var autoCast: Boolean
        get() = prefs.getBoolean("autoCast", false)
        set(v) {
            prefs.edit().putBoolean("autoCast", v).commit()
        }

    var reconnect: Boolean
        get() = prefs.getBoolean("reconnect", false)
        set(v) {
            prefs.edit().putBoolean("reconnect", v).commit()
        }

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoringEnabled", true)
        set(v) {
            prefs.edit().putBoolean("monitoringEnabled", v).commit()
        }

    var monitorCasting: Boolean
        get() = prefs.getBoolean("monitorCasting", true)
        set(v) {
            prefs.edit().putBoolean("monitorCasting", v).commit()
        }

    var remoteEnabled: Boolean
        get() = prefs.getBoolean("remoteEnabled", false)
        set(v) {
            prefs.edit().putBoolean("remoteEnabled", v).commit()
        }

    var origin: String
        get() = prefs.getString("origin", "")!!
        set(v) {
            if (v.isNotEmpty()) SecurityPolicy.tailnetEndpoint(v)
            prefs.edit().putString("origin", v.trimEnd('/')).commit()
        }

    var saverSeconds: Int
        get() = prefs.getInt("saverSeconds", 180)
        set(v) {
            require(v in 30..3600)
            prefs.edit().putInt("saverSeconds", v).commit()
        }

    var deviceId: String
        get() = prefs.getString("deviceId", "")!!
        set(v) {
            require(Regex("[A-Za-z0-9_-]{1,80}").matches(v))
            prefs.edit().putString("deviceId", v).commit()
        }

    var commandKey: String
        get() = prefs.getString("commandKey", "")!!
        set(v) {
            prefs.edit().putString("commandKey", v).commit()
        }

    var sequence: Long
        get() = prefs.getLong("sequence", 0)
        set(v) {
            check(prefs.edit().putLong("sequence", v).commit())
        }

    var campaignRevision: Long
        get() = prefs.getLong("campaignRevision", 0)
        set(v) {
            prefs.edit().putLong("campaignRevision", v).commit()
        }

    var allowedApps: Set<String>
        get() =
            prefs
                .getStringSet("allowedApps", setOf("com.ionitech.airscreen", "com.tailscale.ipn"))!!
                .toSet()
        set(v) {
            require(v.all { Regex("[A-Za-z0-9_.]+").matches(it) })
            prefs.edit().putStringSet("allowedApps", v).commit()
        }

    var cinematic: Boolean
        get() = prefs.getBoolean("cinematic", true)
        set(v) {
            prefs.edit().putBoolean("cinematic", v).apply()
        }

    var animationQuality: String
        get() = prefs.getString("animationQuality", "auto")!!
        set(v) {
            require(v in setOf("auto", "low", "balanced", "high"))
            prefs.edit().putString("animationQuality", v).apply()
        }

    var reducedMotion: Boolean
        get() = prefs.getBoolean("reducedMotion", false)
        set(v) {
            prefs.edit().putBoolean("reducedMotion", v).apply()
        }

    var showFeatures: Boolean
        get() = prefs.getBoolean("showFeatures", true)
        set(v) {
            prefs.edit().putBoolean("showFeatures", v).apply()
        }

    var terminalEnabled: Boolean
        get() = prefs.getBoolean("terminalEnabled", true)
        set(v) {
            prefs.edit().putBoolean("terminalEnabled", v).apply()
        }

    var typingSpeed: Int
        get() = prefs.getInt("typingSpeed", 110)
        set(v) {
            require(v in 30..240)
            prefs.edit().putInt("typingSpeed", v).apply()
        }

    var terminalSize: Int
        get() = prefs.getInt("terminalSize", 19)
        set(v) {
            require(v in 14..22)
            prefs.edit().putInt("terminalSize", v).apply()
        }

    var intensity: Float
        get() = prefs.getFloat("intensity", 1f)
        set(v) {
            require(v in .1f..1.5f)
            prefs.edit().putFloat("intensity", v).apply()
        }

    var clock24: Boolean
        get() = prefs.getBoolean("clock24", false)
        set(v) {
            prefs.edit().putBoolean("clock24", v).apply()
        }

    var weatherProvider: String
        get() = prefs.getString("weatherProvider", "open-meteo")!!
        set(v) {
            require(v in setOf("open-meteo", "aos-core"))
            prefs.edit().putString("weatherProvider", v).apply()
        }

    var weatherLocation: String
        get() = prefs.getString("weatherLocation", "")!!
        set(v) {
            require(v.length <= 40)
            prefs.edit().putString("weatherLocation", v).apply()
        }

    var weatherLatitude: Double
        get() = prefs.getString("weatherLatitude", "0")!!.toDouble()
        set(v) {
            require(v in -90.0..90.0)
            prefs.edit().putString("weatherLatitude", v.toString()).apply()
        }

    var weatherLongitude: Double
        get() = prefs.getString("weatherLongitude", "0")!!.toDouble()
        set(v) {
            require(v in -180.0..180.0)
            prefs.edit().putString("weatherLongitude", v.toString()).apply()
        }

    var weatherRefreshMinutes: Int
        get() = prefs.getInt("weatherRefreshMinutes", 30)
        set(v) {
            require(v in 15..120)
            prefs.edit().putInt("weatherRefreshMinutes", v).apply()
        }

    var weatherCache: String
        get() = prefs.getString("weatherCache", "")!!
        set(v) {
            prefs.edit().putString("weatherCache", v).apply()
        }

    var sshEnabled: Boolean
        get() = prefs.getBoolean("sshEnabled", false)
        set(v) {
            prefs.edit().putBoolean("sshEnabled", v).commit()
        }

    var sshPort: Int
        get() = prefs.getInt("sshPort", 8022)
        set(v) {
            require(v == 0 || v in 1024..65535)
            prefs.edit().putInt("sshPort", v).apply()
        }

    fun initializeWeatherLocation(context: Context) {
        if (prefs.contains("weatherLocation")) return
        val j =
            org.json.JSONObject(
                context.assets.open("cinematic/weather-location.json").bufferedReader().use {
                    it.readText()
                },
            )
        prefs
            .edit()
            .putString("weatherLocation", j.getString("name"))
            .putString("weatherLatitude", j.getDouble("latitude").toString())
            .putString("weatherLongitude", j.getDouble("longitude").toString())
            .apply()
    }

    var diagnosticPolicy: DiagnosticPolicy
        get() = runCatching {
            DiagnosticPolicy.parse(org.json.JSONObject(prefs.getString("diagnostics", "{}")!!))
        }
            .getOrElse { DiagnosticPolicy() }
        set(v) {
            prefs.edit().putString("diagnostics", v.toJson().toString()).apply()
        }

    var internetProbe: String
        get() =
            prefs.getString("internetProbe", "https://connectivitycheck.gstatic.com/generate_204")!!
        set(v) {
            SecurityPolicy.https(v)
            prefs.edit().putString("internetProbe", v).apply()
        }

    private fun key(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("visora-device-token", null) as? javax.crypto.SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "visora-device-token",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
    }

    fun token(): String? {
        val value = prefs.getString("token", null) ?: return null
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            prefs.edit().remove("token").commit()
            null
        }
    }

    fun storeToken(token: String) {
        require(token.length in 16..4096)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        check(
            prefs
                .edit()
                .putString(
                    "token",
                    Base64.encodeToString(c.iv + c.doFinal(token.toByteArray()), Base64.NO_WRAP),
                )
                .commit(),
        )
    }

    fun resetRegistration() {
        prefs
            .edit()
            .remove("token")
            .remove("deviceId")
            .remove("commandKey")
            .putLong("sequence", 0)
            .putBoolean("remoteEnabled", false)
            .commit()
    }
}
