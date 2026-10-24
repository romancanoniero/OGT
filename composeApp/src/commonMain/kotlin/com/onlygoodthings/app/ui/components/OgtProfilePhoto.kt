package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.ui.screens.OgtPostImage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Foto de una persona. Si hay `photo_url` (local o el nodo en vivo), esa URL gana.
 * El drawable queda solo cuando todavía no subió una foto.
 */
@Composable
fun OgtProfilePhoto(
    userId: String?,
    fallback: DrawableResource,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val session = LocalOgtSession.current
    val me = session.me()
    val live = session.livePhotos
    val url = when {
        userId.isNullOrBlank() -> null
        else -> live[userId]
            ?: me.takeIf { it.owns(userId) }?.photoUrl?.takeIf { it.isNotBlank() }
            ?: LocalOgtDb.current.userOrNull(userId)?.photoUrl?.takeIf { it.isNotBlank() }
    }
    if (url != null) {
        OgtPostImage(
            url = url,
            assetKey = "",
            fallback = fallback,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painterResource(fallback),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
