package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.platform.sharePlainText
import com.onlygoodthings.app.theme.OgtColors

/** Hoja de share: diario, contactos o hoja nativa. */
@Composable
fun PostShareSheet(
    shareText: String,
    onDismiss: () -> Unit,
    onRepost: () -> Unit,
    onInvite: () -> Unit,
    onExternal: () -> Unit = {},
    title: String = "Compartir",
) {
    OgtBottomSheet(onDismiss = onDismiss, maxHeight = 360.dp) { sheet ->
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
        ShareChoice("Repostear en el diario") {
            onRepost()
            sheet.close()
        }
        ShareChoice("Enviar a contactos") {
            onInvite()
            sheet.closeNow()
        }
        ShareChoice("WhatsApp, Telegram y más") {
            onExternal()
            sharePlainText(shareText)
            sheet.close()
        }
    }
}

@Composable
private fun ShareChoice(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = OgtColors.ink,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}
