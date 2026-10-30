package com.onlygoodthings.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors

/** The person being honoured is independent of the post's author and topic. */
@Composable
fun HonoreeTitle(name: String?, modifier: Modifier = Modifier, detail: Boolean = false) {
    val title = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
    Text(
        title,
        modifier = modifier.semantics { heading() },
        fontWeight = FontWeight.Bold,
        fontSize = if (detail) 22.sp else 18.sp,
        lineHeight = if (detail) 28.sp else 24.sp,
        color = OgtColors.ink,
    )
}
