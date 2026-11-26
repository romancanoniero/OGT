package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.OgtPreviewStore
import com.onlygoodthings.app.theme.LushImpactTheme
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LoginScreen(
    onEnter: () -> Unit,
    onSignUp: () -> Unit,
    onRecover: () -> Unit,
    onOtp: () -> Unit
) {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var canBiometric by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        canBiometric = auth.canUseBiometric()
    }

    AuthScaffold {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OgtLogo(size = 64.dp)
                OgtPill("Red de impacto comunitario")
            }

            AuthFeatureReel(Modifier.padding(top = 4.dp))
            OgtSectionTitle("Bienvenido de vuelta")
        }
        AuthField("Correo o teléfono", "correo@comunidad.org o tu teléfono", id) { id = it }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            AuthField("Contraseña", "Tu clave secreta", password, secret = true) { password = it }
            GhostLink(
                modifier = Modifier.padding(top = 1.dp).height(IntrinsicSize.Min),
                text = "¿Olvidaste tu contraseña?",
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterEnd
            ) { onRecover() }
        }

        AuthError(auth.error)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OgtPrimaryButton(
                if (auth.busy) "Ingresando…" else "Ingresar a mi comunidad",
                enabled = !auth.busy,
                modifier = Modifier.weight(1f),
            ) {
                scope.launch {
                    val ok = auth.signInIdentifier(id, password)
                    when {
                        auth.pendingPhone != null -> onOtp()
                        ok -> onEnter()
                    }
                }
            }
            if (canBiometric) {
                Box(
                    Modifier.size(50.dp)
                        .clip(AuthCorner)
                        .background(OgtColors.sand)
                        .clickable {
                            scope.launch {
                                if (auth.unlockWithBiometric()) {
                                    auth.enableBiometricAfterLogin()
                                    onEnter()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BiometricGlyph()
                }
            }
        }
        Text(
            "O CON GOOGLE / APPLE",
            color = OgtColors.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        AuthProviderRow(enabled = !auth.busy) { provider ->
            scope.launch {
                if (auth.signInProvider(provider)) onEnter()
            }
        }
        Text(
            "Compartí buenas noticias y acciones solidarias con tu comunidad.",
            color = OgtColors.muted,
            fontSize = 13.sp
        )
        GhostLink("¿Aún no eres parte de la red? Regístrate aquí") { onSignUp() }
    }
}

@Composable
private fun BiometricGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        drawArc(
            color = OgtColors.ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.7f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = OgtColors.primary,
            radius = w * 0.15f,
            center = Offset(w * 0.5f, h * 0.6f)
        )
    }
}

@Preview
@Composable
private fun LoginScreenPreview() {
    LushImpactTheme {
        OgtPreviewStore {
            LoginScreen(onEnter = {}, onSignUp = {}, onRecover = {}, onOtp = {})
        }
    }
}
