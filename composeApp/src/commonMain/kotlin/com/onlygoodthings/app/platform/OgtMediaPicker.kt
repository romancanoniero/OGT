package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaRules

/** Foto o video elegido en el dispositivo, ya copiado a caché de la app. */
data class OgtPickedMedia(
    val id: String,
    val kind: MediaKind,
    val path: String,
    val fromDevice: Boolean,
    val posterPath: String? = null,
    /** URL ya subida. La vista puede seguir usando el archivo local. */
    val uploadedUrl: String? = null,
)

/** Lo que se muestra en la tira: el archivo local, si todavía está. */
fun OgtPickedMedia.previewUrl(): String =
    if (fromDevice) "file://$path" else uploadedUrl?.takeIf { it.isNotBlank() } ?: "asset://$path"

/** Lo que se publica: la foto ya subida, encuadrada. */
fun OgtPickedMedia.publishUrl(): String =
    uploadedUrl?.takeIf { it.isNotBlank() }
        ?: if (fromDevice) "file://$path" else "asset://$path"

class OgtMediaPicker(
    val pickPhoto: () -> Unit,
    val pickVideo: () -> Unit,
    val pickLibrary: () -> Unit,
    val takeCamera: () -> Unit,
)

/** Galería (una o varias), videos o cámara del sistema. */
@Composable
fun rememberOgtMediaPicker(onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker =
    rememberOgtMediaPicker(PostMediaRules.MAX_ITEMS, onPicked)

@Composable
expect fun rememberOgtMediaPicker(remaining: Int, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker
