package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtLoader
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.data.local.LocalHonorMention
import com.onlygoodthings.shared.domain.HonorStatus
import kotlinx.coroutines.launch

/** Reivindica una mención de honor con el token del link. */
@Composable
fun ClaimHonorScreen(onDone: () -> Unit, onBack: () -> Unit = onDone) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val auth = LocalAuth.current
    val me = session.me()
    val scope = rememberCoroutineScope()
    val token = session.pendingHonorToken.orEmpty()
    var honor by remember(token) { mutableStateOf(db.honorByToken(token)) }
    var loading by remember { mutableStateOf(honor == null && token.isNotBlank()) }
    var claimed by remember { mutableStateOf(honor.claimedBy(me.id)) }
    var blocked by remember { mutableStateOf(honor.blockedFor(me.id)) }
    LaunchedEffect(token) {
        if (token.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        val remote = auth.honor.peek(token)
        if (remote != null) {
            honor = db.upsertHonorFromRemote(remote)
            session.persistHonors()
        }
        claimed = honor.claimedBy(me.id)
        blocked = honor.blockedFor(me.id)
        loading = false
    }
    val issuerLabel = honor?.issuerName
        ?: honor?.issuerUserId?.let { db.userOrNull(it)?.displayName }
        ?: "Alguien de la comunidad"
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Reivindicar mención", onBack = onBack)
        ScreenColumn {
            when {
                loading -> {
                    OgtSectionTitle("Buscando la mención")
                    OgtLoader(size = 28.dp)
                    OgtCaption("Estamos abriendo el crédito que te dieron.")
                }
                honor == null -> {
                    OgtSectionTitle("Este link no sirve")
                    OgtCaption("El enlace está incompleto o ya no existe. Pedile a quien te mencionó que te lo reenvíe.")
                    OgtPrimaryButton("Volver") { onBack() }
                }
                blocked -> {
                    OgtSectionTitle("Ya tiene dueña")
                    OgtCaption("Otra persona ya reivindicó esta mención. Si fue un error, hablalo con $issuerLabel.")
                    OgtPrimaryButton("Entendido") { onBack() }
                }
                claimed -> {
                    OgtSectionTitle("Ya es tuya")
                    OgtCaption("El crédito quedó a tu nombre. El nick que elijas reemplaza el nombre que te dieron.")
                    OgtPrimaryButton("Seguir") { onDone() }
                }
                else -> {
                    OgtSectionTitle("¿Eras vos?")
                    OgtCard {
                        Text(
                            "$issuerLabel te mencionó como ${honor?.givenName}.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = OgtColors.ink,
                        )
                        Spacer(Modifier.height(8.dp))
                        OgtCaption("Si confirmás, el crédito de esa buena acción queda en tu perfil. El nombre dado se reemplaza por tu nick.")
                    }
                    OgtPrimaryButton("Sí, era yo") {
                        scope.launch {
                            val local = db.claimHonor(token, me.id)
                            val remote = auth.honor.claim(token)
                            if (remote != null) db.upsertHonorFromRemote(remote)
                            session.persistHonors()
                            if (local == null && remote == null) blocked = true else claimed = true
                        }
                    }
                    GhostLink("Ahora no") { onBack() }
                }
            }
        }
    }
}

private fun LocalHonorMention?.claimedBy(userId: String): Boolean =
    this?.status == HonorStatus.CLAIMED && this.claimedUserId == userId

private fun LocalHonorMention?.blockedFor(userId: String): Boolean =
    this?.status == HonorStatus.CLAIMED && this.claimedUserId != userId
