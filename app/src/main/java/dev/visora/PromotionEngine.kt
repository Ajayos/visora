package dev.visora

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONObject

class PromotionEngine(private val context: Context) {
    private val manifest = AtomicFile(File(context.filesDir, "campaign.json"))
    val media = File(context.filesDir, "media").apply { mkdirs() }
    @Volatile
    var campaign: Campaign? = load()
        private set

    private fun load(): Campaign? = runCatching {
        Campaign.parse(JSONObject(manifest.openRead().bufferedReader().use { it.readText() }))
    }
        .getOrNull()

    fun content(hash: String): File {
        require(Regex("[a-f0-9]{64}").matches(hash))
        return File(media, hash)
    }

    fun install(json: JSONObject, fetch: (String, File, Long) -> Unit) {
        val next =
            try {
                Campaign.parse(json)
            } catch (e: Exception) {
                dev.visora.cinematic.RuntimeEvents.publish(
                    dev.visora.cinematic.EventLevel.ERROR,
                    "config",
                    "Campaign validation failed (${e.javaClass.simpleName}); existing content retained",
                )
                throw e
            }
        require(next.revision > (campaign?.revision ?: 0)) { "Stale campaign" }
        val required =
            next.slides
                .filter { it.kind != "text" }
                .map {
                    Triple(
                        it.sha256,
                        it.url,
                        if (it.kind == "video") 80L * 1024 * 1024 else 12L * 1024 * 1024,
                    )
                }
                .toMutableList()
        if (next.brand.logo.isNotEmpty())
            required.add(
                Triple(
                    next.brand.logo,
                    json.getJSONObject("brand").getString("logoUrl"),
                    4L * 1024 * 1024,
                ),
            )
        try {
            for ((hash, url, max) in required.distinctBy { it.first }) {
                val file = content(hash)
                if (file.exists() && digest(file) == hash) continue
                require(media.usableSpace > max + 100L * 1024 * 1024) { "Insufficient storage" }
                val temp = File(media, "$hash.part")
                try {
                    fetch(url, temp, max)
                    require(digest(temp) == hash) { "Content checksum mismatch" }
                    check(temp.renameTo(file))
                } finally {
                    temp.delete()
                }
                require((media.listFiles()?.sumOf { it.length() } ?: 0) <= 256L * 1024 * 1024) {
                    "Cache quota exceeded"
                }
            }
            var out: FileOutputStream? = null
            try {
                out = manifest.startWrite()
                out.write(json.toString().toByteArray())
                manifest.finishWrite(out)
            } catch (e: Exception) {
                if (out != null) manifest.failWrite(out)
                throw e
            }
            campaign = next
            dev.visora.cinematic.RuntimeEvents.publish(
                dev.visora.cinematic.EventLevel.OK,
                "content",
                "Campaign revision ${next.revision} published after checksum validation",
            )
        } finally {
            val live = campaign
            val keep =
                live?.slides?.map { it.sha256 }?.toSet().orEmpty() + (live?.brand?.logo ?: "")
            media.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    fun slides(brand: String, now: Long = System.currentTimeMillis()): List<Slide> =
        campaign?.takeIf { it.brand.id == brand }?.active(now).orEmpty()

    companion object {
        fun digest(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(32768)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    md.update(buffer, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
