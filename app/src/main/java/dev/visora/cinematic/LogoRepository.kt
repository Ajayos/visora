package dev.visora.cinematic

import android.content.Context
import android.graphics.*
import java.io.File

/** Only these two small regions of the supplied composite are decoded. Never drawn as wallpaper. */
class LogoRepository(private val context: Context) {
    @Suppress("DEPRECATION")
    fun load(brand: String, override: File? = null): Bitmap {
        require(brand in setOf("aos", "deluper"))
        if (override?.isFile == true) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(override.path, bounds)
            require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
            var sample = 1
            while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 320) sample *= 2
            return requireNotNull(
                BitmapFactory.decodeFile(
                    override.path,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ),
            )
        }
        // Reference is 1672×941. There is no shared AOS/Deluper logo asset or lockup.
        val region = if (brand == "aos") Rect(589, 164, 1081, 308) else Rect(54, 32, 414, 92)
        return context.assets.open("cinematic/logo-source.png").use { stream ->
            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
            try {
                val decoded =
                    requireNotNull(
                        decoder.decodeRegion(
                            region,
                            BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            },
                        ),
                    )
                val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
                decoded.recycle()
                bitmap.setHasAlpha(true)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val clean =
                    LogoMatte.extract(pixels, bitmap.width, bitmap.height, brand == "deluper")
                bitmap.setPixels(clean, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap
            } finally {
                decoder.recycle()
            }
        }
    }
}
