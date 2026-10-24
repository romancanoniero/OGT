package com.onlygoodthings.app.platform

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.FileInputStream

/**
 * El archivo de caché es privado de la app. VideoView abre el path desde mediaserver
 * y no puede leerlo. El descriptor se pasa por el proceso de la app.
 */
@Composable
actual fun OgtPostedVideo(url: String, modifier: Modifier) {
    val path = when {
        url.startsWith("file://") -> url.removePrefix("file://")
        url.startsWith("/") -> url
        else -> ""
    }
    AndroidView(
        factory = { context -> DiskVideo(context) },
        update = { view ->
            if (path.isBlank() || view.tag == path) return@AndroidView
            view.tag = path
            view.play(path)
        },
        modifier = modifier,
        onRelease = { it.stop() },
    )
}

private class DiskVideo(context: android.content.Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var playing: String? = null

    init {
        surfaceTextureListener = this
    }

    fun play(path: String) {
        if (path.isBlank() || path == playing) return
        playing = path
        if (isAvailable) bind(path)
    }

    fun stop() {
        playing = null
        releasePlayer()
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        playing?.let { bind(it) }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    private fun bind(path: String) {
        releasePlayer()
        val texture = surfaceTexture ?: return
        val next = MediaPlayer()
        val nextSurface = Surface(texture)
        player = next
        surface = nextSurface
        try {
            FileInputStream(path).use { input -> next.setDataSource(input.fd) }
            next.setSurface(nextSurface)
            next.isLooping = true
            next.setOnPreparedListener { ready -> ready.start() }
            next.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
            playing = null
        }
    }

    private fun releasePlayer() {
        player?.runCatching {
            setOnPreparedListener(null)
            release()
        }
        player = null
        surface?.release()
        surface = null
    }
}
