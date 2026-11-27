package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Control-free player used by inline and full-screen post media. */
@Composable
expect fun OgtPostedVideo(url: String, modifier: Modifier = Modifier, playing: Boolean = true, muted: Boolean = false, onEnded: () -> Unit = {})
