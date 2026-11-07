package dev.visora

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File
import org.json.JSONObject

/** Device-only integration tests; all cache fixtures live in the TEST APK's private data. */
class DeviceTestRunner : Instrumentation() {
    private var background = false
    private var cinematic = false
    private var inspect = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        background = arguments?.getString("background") == "true"
        inspect = arguments?.getString("inspect") == "true"
        cinematic = arguments?.getString("cinematic") == "true"
        start()
    }

    override fun onStart() {
        if (background) {
            val outcome = runCatching { BackgroundDeviceChecks.run(this) }
            finish(
                if (outcome.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply {
                    putString("stream", outcome.getOrElse { "FAIL: ${it.stackTraceToString()}" })
                },
            )
            return
        }
        if (inspect) {
            val lines = mutableListOf<String>()
            for (pkg in listOf("com.tailscale.ipn", "com.ionitech.airscreen")) {
                val info =
                    targetContext.packageManager.getPackageInfo(
                        pkg,
                        android.content.pm.PackageManager.GET_RECEIVERS or
                            android.content.pm.PackageManager.GET_SERVICES,
                    )
                lines.add("$pkg version=${info.versionName}")
                info.receivers
                    .orEmpty()
                    .filter {
                        it.name.contains("Boot") ||
                            it.name.contains("IPN") ||
                            it.name.contains("Reschedule")
                    }
                    .forEach {
                        lines.add(
                            "receiver ${it.name} exported=${it.exported} enabled=${it.enabled} permission=${it.permission}",
                        )
                    }
                info.services
                    .orEmpty()
                    .filter {
                        it.name.contains("IPN") ||
                            it.name.contains("Native") ||
                            it.name.contains("ReceiverService")
                    }
                    .forEach {
                        lines.add(
                            "service ${it.name} exported=${it.exported} enabled=${it.enabled} permission=${it.permission}",
                        )
                    }
            }
            finish(
                Activity.RESULT_OK,
                Bundle().apply { putString("stream", lines.joinToString("\n")) },
            )
            return
        }
        val isolated =
            object : android.content.ContextWrapper(targetContext) {
                override fun getFilesDir(): File =
                    File(targetContext.cacheDir, "integration-fixtures").apply { mkdirs() }

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): android.content.SharedPreferences =
                    targetContext.getSharedPreferences("integration-$name", mode)
            }
        val results = Bundle()
        try {
            val fixture = "verified image fixture".toByteArray()
            val hash = SecurityPolicy.hash(fixture)
            fun campaign(rev: Int, digest: String = hash): JSONObject =
                JSONObject(
                    """{"revision":$rev,"brand":{"id":"aos","name":"AOS","eyebrow":"Test","headline":"Test","body":"Test","accent":"#B9F36A","background":"#080D14"},"slides":[{"id":"image","brand":"aos","kind":"image","sha256":"$digest","url":"https://test.example.ts.net/image","seconds":5}]}""",
                )
            isolated.filesDir.listFiles()?.forEach { it.deleteRecursively() }
            val engine = PromotionEngine(isolated)
            engine.install(campaign(1)) { _, file, _ -> file.writeBytes(fixture) }
            check(engine.campaign?.revision == 1L)
            check(PromotionEngine(isolated).campaign?.revision == 1L) {
                "Offline process restart lost manifest"
            }
            check(engine.content(hash).readBytes().contentEquals(fixture))
            val otherHash = SecurityPolicy.hash("other".toByteArray())
            check(
                runCatching {
                    engine.install(campaign(2, otherHash)) { _, file, _ ->
                        file.writeBytes(fixture)
                    }
                }
                    .isFailure,
            ) {
                "Corrupt asset was accepted"
            }
            check(PromotionEngine(isolated).campaign?.revision == 1L) {
                "Bad update replaced last good manifest"
            }
            check(engine.content(hash).exists())
            check(!engine.content(otherHash).exists())
            check(
                runCatching {
                    engine.install(campaign(2, otherHash)) { _, _, _ -> error("Offline") }
                }
                    .isFailure,
            )
            check(PromotionEngine(isolated).campaign?.revision == 1L)
            check(
                runCatching { engine.install(campaign(1)) { _, _, _ -> error("Should not fetch") } }
                    .isFailure,
            ) {
                "Revision rollback accepted"
            }
            val settings = SettingsManager(isolated)
            settings.storeToken("device-test-token-0123456789")
            check(SettingsManager(isolated).token() == "device-test-token-0123456789") {
                "Keystore roundtrip failed"
            }
            settings.sequence = 42L
            check(SettingsManager(isolated).sequence == 42L)
            settings.resetRegistration()
            check(settings.token() == null && !settings.remoteEnabled)
            val component =
                android.content.ComponentName("com.tailscale.ipn", "com.tailscale.ipn.IPNReceiver")
            val receiver = targetContext.packageManager.getReceiverInfo(component, 0)
            check(receiver.exported && receiver.permission == null)
            val cinematicResult = if (cinematic) CinematicDeviceChecks.run(this) else ""
            results.putString(
                "stream",
                cinematicResult +
                    "\nPASS: atomic cache publish, restart persistence, corrupt asset rejection, offline retention, stale revision rejection, AndroidKeyStore roundtrip, durable sequence, enrollment reset, installed Tailscale receiver visibility. No VPN disconnection performed.\n",
            )
            finish(Activity.RESULT_OK, results)
        } catch (e: Throwable) {
            results.putString(
                "stream",
                "FAIL: ${e.javaClass.name}: ${e.message}\n${e.stackTraceToString()}",
            )
            finish(Activity.RESULT_CANCELED, results)
        }
    }
}
