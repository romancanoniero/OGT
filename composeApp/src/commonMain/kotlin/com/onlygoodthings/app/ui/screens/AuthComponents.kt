package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    otp: Boolean = false,
    onChange: (String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().then(
                if (otp) Modifier.semantics { contentType = ContentType.SmsOtpCode } else Modifier,
            ),
            placeholder = { Text(hint) },
            visualTransformation = if (secret && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = when {
                otp -> KeyboardOptions(keyboardType = KeyboardType.Number)
                secret -> KeyboardOptions(keyboardType = KeyboardType.Password)
                else -> KeyboardOptions.Default
            },
            trailingIcon = if (secret) {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.semantics {
                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                        },
                    ) {
                        PasswordEyeMark(visible = passwordVisible)
                    }
                }
            } else null,
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


@Composable
private fun PasswordEyeMark(visible: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val eye = Path().apply {
            moveTo(w * 0.08f, h * 0.50f)
            cubicTo(w * 0.32f, h * 0.10f, w * 0.68f, h * 0.10f, w * 0.92f, h * 0.50f)
            cubicTo(w * 0.68f, h * 0.90f, w * 0.32f, h * 0.90f, w * 0.08f, h * 0.50f)
            close()
        }
        val stroke = w * 0.085f
        drawPath(eye, OgtColors.ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(OgtColors.ink, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f))
        if (visible) {
            drawLine(
                color = OgtColors.ink,
                start = Offset(w * 0.13f, h * 0.13f),
                end = Offset(w * 0.87f, h * 0.87f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
