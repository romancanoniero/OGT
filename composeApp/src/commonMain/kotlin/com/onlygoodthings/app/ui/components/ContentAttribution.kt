package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.domain.ContentAttribution

@Composable
fun AttributionField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    Column {
        OgtOutlinedTextField(value, { if (it.length <= ContentAttribution.MAX_URL_LENGTH) onChange(it) },
            Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Fuente o enlace original (opcional)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
            singleLine = true)
        OgtCaption("Indicá el enlace original cuando corresponda. Dar crédito no reemplaza el permiso de uso.")
    }
}

@Composable
fun PublicationRightsNotice() {
    val uri = LocalUriHandler.current
    Column {
        OgtCaption(ContentAttribution.NOTICE)
        TextButton(onClick = { uri.openUri("https://onlygoodthings.lat/terms#propiedad-intelectual") }) { Text("Leer el acuerdo de publicación") }
    }
}

@Composable
fun AttributionLink(value: String?, color: Color = OgtColors.primary) {
    val safe = runCatching { ContentAttribution.normalize(value) }.getOrNull() ?: return
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri(safe) }) { Text(ContentAttribution.label(safe), color = color) }
}
