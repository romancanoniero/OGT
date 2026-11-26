package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.*
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.domain.normalizeNickname
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NicknameEditor() {
    val auth=LocalAuth.current;val db=LocalOgtDb.current;val session=LocalOgtSession.current;val me=session.me();val scope=rememberCoroutineScope()
    var nickname by remember(me.id,me.nickname) { mutableStateOf(me.nickname.orEmpty()) }
    var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OgtCaption("Tu nombre completo aparece en reconocimientos. El @nick sirve para encontrar tu cuenta.")
        OgtOutlinedTextField(value=nickname,onValueChange={nickname=it;message=null},label={Text("@nick")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        OgtPrimaryButton("Guardar @nick",enabled=!busy) {
            try {
                val value=normalizeNickname(nickname);busy=true
                scope.launch {
                    try {
                        val saved=auth.feed.setNickname(value)
                        check(session.currentUserId==me.id) { "La sesión cambió." }
                        db.applyUserLive(saved.id,saved.displayName,saved.photoUrl,saved.nickname)
                        session.overlayUser=session.me().copy(nickname=saved.nickname)
                        session.persistSocialFeed();message="@nick guardado."
                    } catch(cancel:CancellationException) { throw cancel } catch(e:Exception) { message=e.message }
                    finally { busy=false }
                }
            } catch(e:IllegalArgumentException) { message=e.message }
        }
        message?.let { OgtCaption(it) }
    }
}
