package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_diffuse
import com.onlygoodthings.app.theme.OgtColors

@Composable
fun PostDiffusionButton(postId:String) {
    val db=LocalOgtDb.current;val auth=LocalAuth.current
    val tick by db.socialTick.collectAsState()
    val post=remember(postId,tick) { db.posts.firstOrNull { it.id==postId } } ?: return
    val active=post.viewerHasDiffused
    val color=if(active)OgtColors.expressionOn else OgtColors.expressionIdle
    Box(Modifier.size(48.dp).semantics { selected=active;contentDescription=if(active)"Dejar de difundir" else "Difundir a tus seguidores" }
        .clickable { auth.diffusePost(postId) },contentAlignment=Alignment.Center) {
        OgtStitchIcon(Res.drawable.qs_diffuse,"",tint=color)
    }
}

@Composable
fun PostDiffusionCredit(postId:String) {
    val db=LocalOgtDb.current
    val me=com.onlygoodthings.app.data.LocalOgtSession.current.me()
    val tick by db.socialTick.collectAsState()
    val post=remember(postId,tick) { db.posts.firstOrNull { it.id==postId } }
    val person=post?.diffusedBy?.firstOrNull { it.userId!=me.id } ?: post?.diffusedBy?.firstOrNull() ?: return
    Text(if(person.userId==me.id)"Difundido por vos" else "${person.displayName} difundió",fontSize=12.sp,color=OgtColors.muted,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
}
