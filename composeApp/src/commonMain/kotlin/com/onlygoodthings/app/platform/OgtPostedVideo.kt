package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Reproduce el video posteado a pantalla completa. Poster si la URL no es remota. */
@Composable
expect fun OgtPostedVideo(url: String, modifier: Modifier = Modifier)
