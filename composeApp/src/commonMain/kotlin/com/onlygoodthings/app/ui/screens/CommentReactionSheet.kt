package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_carlos
import com.onlygoodthings.app.resources.feed_avatar_mariana
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_avatar_reply
import com.onlygoodthings.app.resources.feed_avatar_roberto
import com.onlygoodthings.app.resources.feed_avatar_sofia
import com.onlygoodthings.app.platform.SoftenDialogBackdrop
import com.onlygoodthings.app.resources.qs_clap
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtIds
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Bottom sheet: quiénes aplaudieron un comentario. */
@Composable
fun CommentReactionSheet(
    people: List<LocalUser>,
    onDismiss: () -> Unit,
) {
    val sheet = RoundedCornerShape(topStart = OgtDimens.cardRadius, topEnd = OgtDimens.cardRadius)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SoftenDialogBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                OgtColors.canvas.copy(alpha = 0.22f),
                                OgtColors.sand.copy(alpha = 0.48f),
                            ),
                        ),
                    )
                    .clickable(onClick = onDismiss),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(sheet)
                    .background(OgtColors.surface)
                    .border(1.dp, OgtColors.hairline, sheet)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(max = 420.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(OgtColors.stone),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OgtStitchIcon(Res.drawable.qs_clap, "Aplausos", tint = OgtColors.secondary)
                    Text("Aplausos", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OgtColors.ink)
                }
                OgtCaption(
                    if (people.isEmpty()) "Todavía no hay reacciones."
                    else "${people.size} · Quienes aplaudieron",
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    people.forEach { who ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Image(
                                painter = painterResource(reactionAvatar(who.id)),
                                contentDescription = who.displayName,
                                modifier = Modifier.size(44.dp).clip(CircleShape).border(1.dp, OgtColors.hairline, CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(who.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OgtColors.ink)
                                OgtCaption(who.levelLabel)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun reactionAvatar(userId: String): DrawableResource = when (userId) {
    OgtIds.Roberto -> Res.drawable.feed_avatar_roberto
    OgtIds.Sofia, OgtIds.Camila -> Res.drawable.feed_avatar_sofia
    OgtIds.CarlosG, OgtIds.CarlosR, OgtIds.DiegoF -> Res.drawable.feed_avatar_carlos
    OgtIds.Mariana, OgtIds.Lucia, OgtIds.MarianaD, OgtIds.Valeria, OgtIds.ValeriaP -> Res.drawable.feed_avatar_mariana
    OgtIds.Lucas -> Res.drawable.feed_avatar_me
    else -> Res.drawable.feed_avatar_reply
}
