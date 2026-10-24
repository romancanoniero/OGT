package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.google_logo
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import org.jetbrains.compose.resources.painterResource

/** Fila de proveedores. Google; el mismo proveedor que en la web. */
@Composable
fun AuthProviderRow(onPick: (String) -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthProviderChip("Google", Modifier.weight(1f), onClick = { onPick("google") }) { GoogleMark() }
    }
}

/** Google en una sola fila, debajo del formulario. */
@Composable
fun AuthGoogleButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(OgtDimens.iconButton)
            .clip(RoundedCornerShape(OgtDimens.buttonRadius))
            .border(1.dp, OgtColors.hairline, RoundedCornerShape(OgtDimens.buttonRadius))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleMark()
        Text(
            "Continuar con Google",
            modifier = Modifier.padding(start = 10.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = OgtColors.ink,
        )
    }
}

@Composable
private fun AuthProviderChip(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier
            .height(72.dp)
            .clip(AuthCorner)
            .background(OgtColors.surface)
            .border(1.dp, OgtColors.stone, AuthCorner)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = OgtColors.ink)
    }
}

@Composable
private fun GoogleMark() {
    Image(
        painter = painterResource(Res.drawable.google_logo),
        contentDescription = "Google",
        modifier = Modifier.size(18.dp),
        contentScale = ContentScale.Fit,
    )
}
