package com.onlygoodthings.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tokens Quiet Studio extraídos de Stitch DESIGN.md. */
object OgtColors {
    val primary = Color(0xFF111213)
    val primaryDeep = Color(0xFF0A0A0B)
    val onPrimary = Color.White
    val secondary = Color(0xFF0D3D4D)
    val tertiary = Color(0xFF5B6570)
    val canvas = Color(0xFFFFFFFF)
    val surface = Color.White
    val sand = Color(0xFFF4F5F7)
    val stone = Color(0xFFE5E7EB)
    val charcoal = Color(0xFF0A0A0B)
    val ink = Color(0xFF111213)
    val muted = Color(0xFF6B7280)
    val mint = Color(0xFFF4F5F7)
    val mintText = Color(0xFF111213)
    val sunset = Color(0xFFF4F5F7)
    val sunsetText = Color(0xFF5B6570)
    val sun = Color(0xFF0D3D4D)
    val hairline = Color(0xFFE5E7EB)
    /** Expresión en reposo: gris, lejos del terracota activo. */
    val expressionIdle = Color(0xFF9CA3AF)
    /** Expresión activa (aplauso, etc.): terracota Quiet Studio. */
    val expressionOn = Color(0xFFC2410C)
    /** Fondo y tinta de un control que no acepta toque. */
    val disabledFill = Color(0xFFD8DCE2)
    val disabledInk = Color(0xFF8B929A)
    val disabledLine = Color(0xFFC5CAD1)
    val error = Color(0xFFBA1A1A)
    val onError = Color.White
    val errorContainer = Color(0xFFFFDAD6)
    val onErrorContainer = Color(0xFF93000A)
}

/** Campos de texto: siempre Quiet Studio, nunca el lila/gris de Material. */
@Composable
fun ogtOutlinedFieldColors(missing: Boolean = false): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OgtColors.ink,
    unfocusedTextColor = OgtColors.ink,
    disabledTextColor = OgtColors.disabledInk,
    cursorColor = if (missing) OgtColors.error else OgtColors.secondary,
    focusedBorderColor = if (missing) OgtColors.error else OgtColors.secondary,
    unfocusedBorderColor = if (missing) OgtColors.error else OgtColors.hairline,
    disabledBorderColor = OgtColors.disabledLine,
    focusedContainerColor = if (missing) OgtColors.errorContainer else OgtColors.sand,
    unfocusedContainerColor = if (missing) OgtColors.errorContainer else OgtColors.sand,
    disabledContainerColor = OgtColors.disabledFill,
    focusedPlaceholderColor = if (missing) OgtColors.onErrorContainer else OgtColors.muted,
    unfocusedPlaceholderColor = if (missing) OgtColors.onErrorContainer else OgtColors.muted,
    disabledPlaceholderColor = OgtColors.disabledInk,
)

object OgtDimens {
    val touch = 52.dp
    val iconButton = 44.dp
    val glyph = 20.dp
    val margin = 20.dp
    val cardRadius = 24.dp
    val pill = 999.dp
    /** CTA: radio corto, no cápsula. */
    val buttonRadius = 10.dp
    val buttonGap = 10.dp
}

private val quietScheme: ColorScheme = lightColorScheme(
    primary = OgtColors.primary,
    onPrimary = OgtColors.onPrimary,
    primaryContainer = OgtColors.sand,
    onPrimaryContainer = OgtColors.ink,
    inversePrimary = OgtColors.stone,
    secondary = OgtColors.secondary,
    onSecondary = OgtColors.onPrimary,
    secondaryContainer = OgtColors.sand,
    onSecondaryContainer = OgtColors.secondary,
    tertiary = OgtColors.tertiary,
    onTertiary = OgtColors.onPrimary,
    tertiaryContainer = OgtColors.sand,
    onTertiaryContainer = OgtColors.ink,
    background = OgtColors.canvas,
    onBackground = OgtColors.charcoal,
    surface = OgtColors.surface,
    onSurface = OgtColors.charcoal,
    surfaceVariant = OgtColors.sand,
    onSurfaceVariant = OgtColors.muted,
    surfaceTint = OgtColors.secondary,
    inverseSurface = OgtColors.primaryDeep,
    inverseOnSurface = OgtColors.sand,
    outline = OgtColors.stone,
    outlineVariant = OgtColors.hairline,
    error = OgtColors.error,
    onError = OgtColors.onError,
    scrim = OgtColors.primaryDeep,
)

@Composable
fun LushImpactTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = quietScheme, content = content)
}
