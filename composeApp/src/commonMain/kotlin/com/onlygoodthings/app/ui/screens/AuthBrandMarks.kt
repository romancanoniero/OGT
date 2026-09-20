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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.auth.showsAppleSignIn
import com.onlygoodthings.app.theme.OgtColors

/** Fila de proveedores. Apple solo en iPhone/iPad. */
@Composable
fun AuthProviderRow(onPick: (String) -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthProviderChip("Google", Modifier.weight(1f), onClick = { onPick("google") }) { GoogleMark() }
        if (showsAppleSignIn()) {
            AuthProviderChip("Apple", Modifier.weight(1f), onClick = { onPick("apple") }) { AppleMark() }
        }
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
private fun AppleMark() {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val body = Path().apply {
            moveTo(s * 0.78f, s * 0.36f)
            cubicTo(s * 0.62f, s * 0.34f, s * 0.56f, s * 0.50f, s * 0.50f, s * 0.50f)
            cubicTo(s * 0.42f, s * 0.50f, s * 0.34f, s * 0.34f, s * 0.20f, s * 0.38f)
            cubicTo(s * 0.06f, s * 0.42f, s * 0.02f, s * 0.62f, s * 0.10f, s * 0.80f)
            cubicTo(s * 0.18f, s * 0.98f, s * 0.32f, s * 1.04f, s * 0.42f, s * 0.96f)
            cubicTo(s * 0.46f, s * 0.93f, s * 0.54f, s * 0.93f, s * 0.58f, s * 0.96f)
            cubicTo(s * 0.70f, s * 1.04f, s * 0.82f, s * 0.96f, s * 0.88f, s * 0.82f)
            cubicTo(s * 0.76f, s * 0.76f, s * 0.72f, s * 0.58f, s * 0.84f, s * 0.50f)
            cubicTo(s * 0.82f, s * 0.44f, s * 0.80f, s * 0.38f, s * 0.78f, s * 0.36f)
            close()
        }
        drawPath(body, Color(0xFF111111))
        val leaf = Path().apply {
            moveTo(s * 0.62f, s * 0.10f)
            cubicTo(s * 0.70f, s * 0.22f, s * 0.62f, s * 0.34f, s * 0.50f, s * 0.34f)
            cubicTo(s * 0.46f, s * 0.22f, s * 0.54f, s * 0.10f, s * 0.62f, s * 0.10f)
            close()
        }
        drawPath(leaf, Color(0xFF111111))
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
