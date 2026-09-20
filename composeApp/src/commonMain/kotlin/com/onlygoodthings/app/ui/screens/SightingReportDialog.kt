package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_comment
import com.onlygoodthings.app.resources.qs_eye
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.neighborAvatarArt
import com.onlygoodthings.shared.data.local.LocalSighting
import org.jetbrains.compose.resources.painterResource

/** Ficha del avistaje: quién lo reportó y qué dijo. No abre el perfil. */
@Composable
fun SightingReportDialog(
    sighting: LocalSighting,
    onDismiss: () -> Unit,
) {
    val db = LocalOgtDb.current
    val who = db.userOrNull(sighting.userId)
    val name = who?.displayName ?: "Vecino"
    val wrote = sighting.wroteNote()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(OgtDimens.cardRadius))
                .background(OgtColors.surface)
                .border(1.dp, OgtColors.hairline, RoundedCornerShape(OgtDimens.cardRadius))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Avistaje", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(neighborAvatarArt(sighting.userId)),
                    contentDescription = name,
                    modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, OgtColors.hairline, CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OgtColors.ink)
                    OgtCaption(listOfNotNull(who?.barrio?.takeIf { it.isNotBlank() }, sighting.timeLabel).joinToString(" · "))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Lo vio")
                if (wrote) OgtPill("Escribió")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OgtStitchIcon(Res.drawable.qs_eye, "Lo vio", tint = OgtColors.secondary)
                if (wrote) OgtStitchIcon(Res.drawable.qs_comment, "Escribió", tint = OgtColors.secondary)
            }
            Text(
                if (wrote) sighting.note else "Lo vio cerca. No dejó un mensaje.",
                color = OgtColors.charcoal,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            OgtPrimaryButton("Cerrar", onClick = onDismiss)
        }
    }
}
