package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.postShareMessage

/** Check ownership between requests; opening the conversation does not itself send a post. */
internal suspend fun AuthSessionApi.sharePostTo(recipient:String,text:String,link:String,isCurrent:()->Boolean) {
    val body=postShareMessage(text,link)
    check(isCurrent()) { "La sesión cambió" }
    val thread=startConversation(recipient,"mensaje")
    check(isCurrent()) { "La sesión cambió" }
    sendMessage(thread.matchId,body)
    check(isCurrent()) { "La sesión cambió" }
}
