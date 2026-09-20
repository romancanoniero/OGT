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
)

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
