package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun AccountMentionPicker(selected:MentionCandidate?,onSelect:(MentionCandidate?)->Unit) {
    val auth=LocalAuth.current;val db=LocalOgtDb.current;val me=LocalOgtSession.current.me()
    var query by remember { mutableStateOf("") };var people by remember { mutableStateOf<List<NeighborCard>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        people=emptyList();error=null
        if(query.trim().length<2)return@LaunchedEffect
        delay(250)
        try { people=auth.feed.search(query.trim().removePrefix("@"),8).people.filter { it.userId!=me.id } }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { error="No pudimos buscar cuentas. Podés reintentar o continuar con el nombre completo." }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(selected!=null) {
            Text("${selected.displayName} · ${selected.atHandle}")
            GhostLink("Cambiar cuenta") { onSelect(null) }
        } else {
            OgtOutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Buscar cuenta por nombre o @nick (opcional)")})
            for(person in people) Text("${person.displayName} · @${person.nickname ?: mentionHandle(person.displayName)}",Modifier.fillMaxWidth().clickable {
                db.applyUserLive(person.userId,person.displayName,person.photoUrl,person.nickname)
                onSelect(MentionCandidate(person.userId,person.displayName,person.nickname ?: mentionHandle(person.displayName)));query="";people=emptyList()
            }.padding(10.dp))
            error?.let { Text(it,color=OgtColors.error) }
        }
    }
}
