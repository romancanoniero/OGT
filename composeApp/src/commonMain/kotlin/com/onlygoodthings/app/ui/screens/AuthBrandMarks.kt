package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.google_logo
import com.onlygoodthings.app.theme.OgtColors
import org.jetbrains.compose.resources.painterResource

/** Misma leyenda y la misma marca G que el botón de la web. */
const val GoogleSignInLabel = "Continuar con Google"

@Composable
fun AuthProviderRow(onPick: (String) -> Unit = {}) {
    AuthProviderChip(GoogleSignInLabel, Modifier.fillMaxWidth(), onClick = { onPick("google") })
}

@Composable
private fun AuthProviderChip(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(52.dp)
            .clip(AuthCorner)
            .background(OgtColors.surface)
            .border(1.dp, OgtColors.stone, AuthCorner)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.google_logo),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OgtColors.ink)
    }
}
