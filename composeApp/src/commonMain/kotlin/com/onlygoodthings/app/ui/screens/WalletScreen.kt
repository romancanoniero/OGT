package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtMark
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.walletMarkArt
import com.onlygoodthings.app.ui.components.walletMarkIsBrand

@Composable
fun WalletScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val nextGoal = 3000
    val missing = (nextGoal - me.communityPoints).coerceAtLeast(0)
    val progress = (me.communityPoints / nextGoal.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Billetera", onBack = onBack)
        ScreenColumn {
            OgtCaption(me.levelLabel)
            Text(
                "${formatPts(me.communityPoints)} pts",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = OgtColors.primary,
            )
            OgtCaption("Próximo: Héroe de la comunidad · Faltan $missing pts")
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(OgtColors.sand)) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(OgtColors.primary))
            }
            OgtCaption("Meta ${formatPts(nextGoal)} pts")
            PrefCategory("Comercios que agradecen", "Un canje es un gracias, no reputación comprada.")
            PrefGroup {
                db.rewards.forEachIndexed { index, reward ->
                    if (index > 0) PrefDivider()
                    PrefLine(
                        title = reward.place,
                        body = "${reward.badge} · ${reward.detail}",
                        trailing = "${reward.costPoints} pts",
                        leading = {
                            OgtMark(
                                walletMarkArt(reward.mark),
                                reward.place,
                                brand = walletMarkIsBrand(reward.mark),
                            )
                        },
                    )
                }
            }
            PrefCategory("Actividad")
            PrefGroup {
                db.karma.filter { it.userId == me.id }.forEachIndexed { index, entry ->
                    if (index > 0) PrefDivider()
                    val sign = if (entry.delta >= 0) "+" else ""
                    PrefLine(
                        title = entry.title,
                        body = entry.place,
                        trailing = "$sign${entry.delta} pts",
                        leading = { OgtMark(walletMarkArt(entry.mark), entry.title) },
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun formatPts(n: Int): String {
    val raw = n.toString()
    return if (raw.length <= 3) raw else raw.dropLast(3) + "." + raw.takeLast(3)
}
