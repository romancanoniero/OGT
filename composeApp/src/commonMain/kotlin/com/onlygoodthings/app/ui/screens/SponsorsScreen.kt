package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*

@Composable
fun SponsorsScreen(onBack: () -> Unit = {}, onContinue: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = "Sumar sponsors", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenColumn {
                Text("Invitá a una empresa a sumarse", fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, color = OgtColors.ink)
                OgtCaption("¿Conocés una empresa que quiera acompañar a la comunidad? Compartile el proyecto para que se contacte con Only Good Things y podamos conversar sobre su participación como sponsor.")
                OgtCaption("Cada propuesta se evalúa con el equipo de OGT.")
                InvitationShareButtons(sponsorInvitationUrl, "Sumarse como sponsor de Only Good Things", sponsorInvitationText)
                onContinue?.let { OgtSecondaryButton("Continuar", onClick = it) }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
