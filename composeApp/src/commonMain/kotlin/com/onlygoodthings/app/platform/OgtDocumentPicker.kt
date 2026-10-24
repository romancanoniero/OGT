package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

/** Abre el selector de foto o PDF. Cancela sin avisar. El nombre es solo para la pantalla. */
@Composable
expect fun rememberOgtDocumentPicker(onPicked: (String) -> Unit): () -> Unit
