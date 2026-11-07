package dev.visora

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import dev.visora.cinematic.*
import java.io.File
import java.util.concurrent.TimeUnit

/** Unprivileged SSH in this application's UID, bound only to the observed VPN address. */
class SshController(private val context: Context) {
    private val settings = SettingsManager(context)
    private var process: Process? = null
    private var address: String? = null
    private val failures = RecoveryGate()
    private var confirmed = false
    private var requestedPort = 0
    private var wasEnabled = false
    private val directory
        get() = File(context.filesDir, "ssh")

    private fun binary(name: String) =
        File(context.applicationInfo.nativeLibraryDir, name).absolutePath

    fun tick(now: Long) {
        if (wasEnabled != settings.sshEnabled) {
            stop()
            failures.reset()
            wasEnabled = settings.sshEnabled
        }
        val wanted =
            if (settings.sshEnabled && NetworkMonitor(context).vpnPresent())
                TailscaleMonitor(context).address()
            else null
        if (wanted == null || settings.sshPort == 0) {
            stop()
            return
        }
        if (process?.isAlive == true && address == wanted && requestedPort == settings.sshPort) {
            if (!confirmed && NetworkStatus(context).ssh(settings.sshPort).contains("LISTENING")) {
                confirmed = true
                failures.reset()
                RuntimeEvents.publish(
                    EventLevel.OK,
                    "ssh",
                    "SSH protocol listener observed on tailnet; administrator authentication tested separately",
                )
            }
            return
        }
        if (address != null && (address != wanted || requestedPort != settings.sshPort)) {
            stop()
            failures.reset()
        }
        if (!failures.due(now)) return
        failures.requested(now)
        try {
            directory.mkdirs()
            Os.chmod(directory.path, 448)
            val keys = File(directory, "authorized_keys")
            require(keys.isFile) { "No authorized key" }
            // Revalidate private configuration before every launch; malformed files fail closed.
            val canonical = SshKeyPolicy.canonical(keys.readText())
            require(keys.readText().trim() == canonical)
            Os.chmod(keys.path, 384)
            val host = File(directory, "host_ed25519")
            if (!host.exists()) {
                val temporary = File(directory, "host_ed25519.new")
                temporary.delete()
                val generator =
                    ProcessBuilder(
                            binary("libvisora_sshkey.so"),
                            "-t",
                            "ed25519",
                            "-f",
                            temporary.path,
                        )
                        .redirectErrorStream(true)
                        .redirectOutput(File(directory, "key-generation.log"))
                        .start()
                if (!generator.waitFor(10, TimeUnit.SECONDS)) {
                    generator.destroyForcibly()
                    error("Host key generation timeout")
                }
                check(
                    generator.exitValue() == 0 &&
                        temporary.length() > 0 &&
                        temporary.renameTo(host),
                )
            }
            Os.chmod(host.path, 384)
            val command =
                listOf(
                    binary("libvisora_sshrun.so"),
                    binary("libvisora_ssh.so"),
                    "-F",
                    "-w",
                    "-D",
                    directory.path,
                    "-r",
                    host.path,
                    "-p",
                    "$wanted:${settings.sshPort}",
                    "-P",
                    File(directory, "server.pid").path,
                    "-I",
                    "300",
                    "-K",
                    "30",
                    "-T",
                    "3",
                )
            val log = File(directory, "server.log")
            if (log.length() > 65536) log.writeText("")
            val builder =
                ProcessBuilder(command)
                    .directory(directory)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            builder.environment()["VISORA_SSH_HOME"] = directory.path
            process = builder.start()
            address = wanted
            requestedPort = settings.sshPort
            RuntimeEvents.publish(
                EventLevel.RUNNING,
                "ssh",
                "Key-only SSH launch requested on $wanted:${settings.sshPort}; banner/auth verification separate",
            )
        } catch (e: Exception) {
            stop()
            RuntimeEvents.publish(
                EventLevel.WARNING,
                "ssh",
                "SSH unavailable (${e.javaClass.simpleName}); bounded retry ${failures.attempts}/3",
            )
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        address = null
        confirmed = false
    }

    companion object {
        fun installPublicKey(context: Context, text: String) {
            val key = SshKeyPolicy.canonical(text)
            val dir =
                File(context.filesDir, "ssh").apply {
                    mkdirs()
                    Os.chmod(path, 448)
                }
            val atomic = AtomicFile(File(dir, "authorized_keys"))
            val out = atomic.startWrite()
            try {
                out.write((key + "\n").toByteArray(Charsets.US_ASCII))
                atomic.finishWrite(out)
                Os.chmod(atomic.baseFile.path, 384)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        }
    }
}
