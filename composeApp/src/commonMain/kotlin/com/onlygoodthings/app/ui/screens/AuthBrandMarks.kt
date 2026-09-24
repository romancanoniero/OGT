package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors

/** Fila de proveedores. Google y Facebook; el mismo par que en la web. */
@Composable
fun AuthProviderRow(onPick: (String) -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthProviderChip("Google", Modifier.weight(1f), onClick = { onPick("google") }) { GoogleMark() }
        AuthProviderChip("Facebook", Modifier.weight(1f), onClick = { onPick("facebook") }) { FacebookMark() }
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
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val stroke = s * 0.18f
        val pad = s * 0.08f
        val arc = Size(s - pad * 2, s - pad * 2)
        val topLeft = Offset(pad, pad)
        drawArc(Color(0xFFEA4335), -40f, 100f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFFFBBC05), 60f, 70f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFF34A853), 130f, 80f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFF4285F4), 210f, 110f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(s * 0.50f, s * 0.44f),
            size = Size(s * 0.42f, stroke),
        )
    }
}

@Composable
private fun FacebookMark() {
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF1877F2)),
        contentAlignment = Alignment.Center,
    ) {
        Text("f", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
