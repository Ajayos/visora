package dev.visora.cinematic

import android.content.Context
import android.graphics.*
import kotlin.math.*

class BrandingLayer(context: Context, private val corner: Boolean = false) : SceneLayer(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val reflectionRect = RectF()
    private val matrix = Matrix()
    private val shimmer =
        LinearGradient(
            0f,
            0f,
            240f,
            0f,
            intArrayOf(Color.TRANSPARENT, 0xA6E8C5FF.toInt(), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
    private val reflectionPaint = Paint()
    private val blend = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val shadow = LightingColorFilter(0x00561A90, 0)
    private var logo: Bitmap? = null
    var brandId = "aos"

    fun setLogo(bitmap: Bitmap?, brand: String) {
        logo = bitmap
        brandId = brand
        invalidate()
    }

    override fun drawScene(canvas: Canvas) {
        val b = logo
        val floating = sin(time * motion * .48).toFloat() * (if (corner) 1.2f else 7f)
        val logoWidth = if (corner) 410f else 610f
        val ratio = b?.let { it.height.toFloat() / it.width } ?: .29f
        val logoHeight = logoWidth * ratio
        val centerY = (if (corner) 74f else 280f) + floating
        val centerX = if (corner) 275f else 960f
        dst.set(
            centerX - logoWidth / 2,
            centerY - logoHeight / 2,
            centerX + logoWidth / 2,
            centerY + logoHeight / 2,
        )
        p.shader = null
        p.xfermode = null
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.typeface = medium
        if (b != null && !b.isRecycled) {
            p.colorFilter = shadow
            p.alpha = (170 * reveal).toInt()
            canvas.save()
            canvas.translate(0f, 5f)
            canvas.drawBitmap(b, null, dst, p)
            canvas.restore()
            p.colorFilter = null // Preserve the supplied logo RGB; reflections are alpha-clipped.
            p.alpha =
                ((.87f + .13f * sin(time * motion * .65).toFloat()) * 255 * reveal)
                    .toInt()
                    .coerceIn(0, 255)
            canvas.drawBitmap(b, null, dst, p)
            p.colorFilter = null
            if (quality > 0) {
                // Small offscreen layer, limited to the wordmark; no fullscreen blur buffer.
                reflectionPaint.alpha = (reveal * 75).toInt()
                val save = canvas.saveLayer(dst, reflectionPaint)
                p.alpha = 255
                canvas.drawBitmap(b, null, dst, p)
                matrix.setTranslate(
                    dst.left + ((time * motion * .12f) % 1.7f) * logoWidth - logoWidth * .3f,
                    0f,
                )
                shimmer.setLocalMatrix(matrix)
                p.shader = shimmer
                p.xfermode = blend
                p.alpha = 255
                reflectionRect.set(dst)
                canvas.drawRect(reflectionRect, p)
                p.shader = null
                p.xfermode = null
                canvas.restoreToCount(save)
            }
        } else {
            p.alpha = (255 * reveal).toInt()
            p.textSize = if (brandId == "aos") 140f else 100f
            canvas.drawText(if (brandId == "aos") "AOS" else "DELUPER", centerX, centerY + 30, p)
        }
        p.textAlign = Paint.Align.LEFT
    }
}
