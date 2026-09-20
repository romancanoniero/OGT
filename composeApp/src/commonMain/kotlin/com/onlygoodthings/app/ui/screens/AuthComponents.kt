package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtBackButton
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import androidx.compose.ui.graphics.Color

/** Esquinas suaves, no pastilla ni rectángulo duro. */
internal val AuthCorner = RoundedCornerShape(3.dp)

@Composable
internal fun AuthScaffold(onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .ogtDismissImeOnScroll()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) OgtBackButton(onBack)
        content()
    }
}

@Composable
internal fun AuthField(
    label: String,
    hint: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint) },
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            maxLines = 1,
            shape = AuthCorner,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OgtColors.primary,
                unfocusedContainerColor = OgtColors.sand,
                focusedContainerColor = OgtColors.surface,
            ),
        )
    }
}

@Composable
internal fun AuthError(message: String?) {
    if (message.isNullOrBlank()) return
    Text(message, color = Color(0xFFB91C1C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

