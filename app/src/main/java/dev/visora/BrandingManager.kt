package dev.visora

import android.content.Context
import org.json.JSONObject

class BrandingManager(private val context: Context) {
    fun bundled(id: String): Brand {
        require(id in setOf("aos", "deluper"))
        val j =
            JSONObject(
                context.assets.open("brands/$id/brand.json").bufferedReader().use { it.readText() },
            )
        return Brand(
            j.getString("id"),
            j.getString("name"),
            j.getString("eyebrow"),
            j.getString("headline"),
            j.getString("body"),
            j.getString("accent"),
            j.getString("background"),
        )
    }
}
