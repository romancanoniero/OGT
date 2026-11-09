package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.onlygoodthings.app.ui.components.OgtOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import kotlinx.coroutines.launch
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll

@Composable
fun ProposeSkillScreen(onDone: () -> Unit) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val me = session.me()
    val auth = com.onlygoodthings.app.data.LocalAuth.current
    val copy = LocalOgtCopy.current
    val scope = rememberCoroutineScope()
    var need by remember { mutableStateOf("") }
    var give by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var triedPublish by remember { mutableStateOf(false) }
    var publishing by remember(me.id) { mutableStateOf(false) }
    var publishError by remember(me.id) { mutableStateOf<String?>(null) }
    var localDraftSaved by remember(me.id) { mutableStateOf(false) }
    val missingNeed = need.trim().isEmpty()
    val missingGive = give.trim().isEmpty()
    val canPublish = !missingNeed && !missingGive
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.tabSkills, onBack = onDone)
        ScreenColumn {
            OgtCaption("Cualquiera puede pedir una mano. Un saber, un servicio o una tarea. El horario lo hablan en el chat.")
            Text("1. ¿Qué necesitás?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = need,
                onValueChange = { if (it.length <= 120) need = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("Bajar un mueble, una clase de masa madre…", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && missingNeed),
            )
            Text("2. ¿Qué das a cambio?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = give,
                onValueChange = { if (it.length <= 120) give = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("Arreglar la bici, cuidar plantas, un trámite…", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && missingGive),
            )
            Text("Nota (si suma)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 280) note = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Sin día ni hora: eso se arregla en privado.", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(),
            )
            if (triedPublish && !canPublish) {
                Text(
                    "Falta ${listOfNotNull(if (missingNeed) "qué necesitás" else null, if (missingGive) "qué das" else null).joinToString(" y ")}.",
                    color = OgtColors.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            publishError?.let { Text(it,color=OgtColors.error) }
            OgtPrimaryButton(if(publishing) "Publicando…" else "Publicar el intercambio",enabled=!publishing) {
                if (!canPublish) {
                    triedPublish = true
                    return@OgtPrimaryButton
                }
                val owner=me.id
                val wanted=need.trim();val offered=give.trim();val detail=note.trim()
                publishing=true;publishError=null
                scope.launch {
                    try {
                        check(session.me().id==owner)
                        auth.account.publishExchange(wanted,offered,detail)
                        check(session.me().id==owner)
                        onDone()
                    } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                    catch(_:Exception) {
                        if(session.me().id==owner) {
                            if(db.helpExchangesOf(owner).none { it.need==wanted && it.give==offered && it.note.orEmpty()==detail })
                                db.publishHelpExchange(owner,wanted,offered,detail)
                            localDraftSaved=true
                            publishError="No se confirmó la publicación. Guardamos tu aviso en este dispositivo; podés reintentar el envío."
                        }
                    } finally { publishing=false }
                }
            }
            if(localDraftSaved)com.onlygoodthings.app.ui.components.OgtSecondaryButton("Ver mi aviso en este dispositivo",onClick=onDone)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OgtCaption("Sin dinero. Una mano por otra.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
