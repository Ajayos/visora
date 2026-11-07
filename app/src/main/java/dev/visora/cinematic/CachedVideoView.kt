package dev.visora.cinematic

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import java.io.File

/**
 * TextureView lets live branding/clock layers composite above a local video without a SurfaceView
 * hole.
 */
class CachedVideoView(
    context: Context,
    private val file: File,
    private val loop: Boolean,
    private val finished: () -> Unit,
    private val failed: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var output: Surface? = null
    private var closed = false
    private val transform = Matrix()

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        try {
            val surface = Surface(texture)
            output = surface
            player =
                MediaPlayer().apply {
                    setSurface(surface)
                    setDataSource(file.path)
                    setVolume(0f, 0f)
                    isLooping = loop
                    setOnPreparedListener { media ->
                        if (closed) return@setOnPreparedListener
                        if (media.videoWidth > 1920 || media.videoHeight > 1080) {
                            close()
                            failed()
                            return@setOnPreparedListener
                        }
                        fit(media.videoWidth, media.videoHeight)
                        media.start()
                    }
                    setOnCompletionListener { if (!closed) finished() }
                    setOnErrorListener { _, _, _ ->
                        close()
                        failed()
                        true
                    }
                    prepareAsync()
                }
        } catch (_: Exception) {
            close()
            failed()
        }
    }

    private fun fit(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || width <= 0 || height <= 0) return
        val videoRatio = w.toFloat() / h
        val viewRatio = width.toFloat() / height
        transform.reset()
        if (videoRatio > viewRatio)
            transform.setScale(videoRatio / viewRatio, 1f, width / 2f, height / 2f)
        else transform.setScale(1f, viewRatio / videoRatio, width / 2f, height / 2f)
        setTransform(transform)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        player?.let { runCatching { fit(it.videoWidth, it.videoHeight) } }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        close()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}

    fun close() {
        closed = true
        player?.let {
            runCatching {
                it.setOnPreparedListener(null)
                it.setOnCompletionListener(null)
                it.setOnErrorListener(null)
                it.release()
            }
        }
        player = null
        output?.release()
        output = null
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }
}
