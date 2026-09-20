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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_carlos
import com.onlygoodthings.app.resources.feed_avatar_mariana
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_avatar_reply
import com.onlygoodthings.app.resources.feed_avatar_roberto
import com.onlygoodthings.app.resources.feed_avatar_sofia
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtBackButton
import com.onlygoodthings.shared.data.local.OgtIds
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Perfil de alguien de la comunidad (no el ajuste de cuenta).
 * El nombre en stories o en el post abre acá, como en Instagram.
 */
@Composable
fun NeighborProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val user = db.userOrNull(userId)
    if (user == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFBF8FC))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(24.dp),
        ) {
            OgtBackButton(onBack)
            Spacer(Modifier.height(16.dp))
            Text("Esta persona ya no está en la comunidad.")
        }
        return
    }
    val posts = remember(userId, db.posts.size) { db.postsOfAuthor(userId) }
    var following by remember(userId, db.follows.size) { mutableStateOf(db.isFollowing(me.id, userId)) }
    val mine = userId == me.id
    val avatar = neighborAvatar(userId)
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF8FC)).verticalScroll(rememberScrollState())) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OgtBackButton(onBack)
            Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(avatar),
                    contentDescription = user.displayName,
                    modifier = Modifier.size(86.dp).clip(CircleShape).border(2.dp, OgtColors.primary, CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(20.dp))
                ProfileStat("${posts.size}", "acciones")
                ProfileStat("${db.followerCount(userId)}", "seguidores")
                ProfileStat("${user.communityPoints}", "pts")
            }
            Spacer(Modifier.height(12.dp))
            Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(user.levelLabel, color = OgtColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${user.barrio}  ·  ${user.honorTag}", color = OgtColors.muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            if (mine) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0EDF1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Este sos vos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            } else {
                val fill = if (following) Color(0xFFF0EDF1) else OgtColors.primary
                val ink = if (following) OgtColors.ink else Color.White
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fill)
                        .clickable {
                            db.toggleFollow(me.id, userId)
                            following = db.isFollowing(me.id, userId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (following) "Siguiendo" else "Seguir", color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Publicaciones",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = OgtColors.muted,
        )
        if (posts.isEmpty()) {
            Text("Todavía no publicó una buena acción.", modifier = Modifier.padding(16.dp), color = OgtColors.muted)
        } else {
            posts.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { post ->
                        val media = db.mediaOf(post.id).firstOrNull()
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clickable { onOpenPost(post.id) },
                        ) {
                            if (media != null) {
                                OgtPostImage(
                                    item = media,
                                    fallback = Res.drawable.feed_avatar_reply,
                                    contentDescription = post.tag,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Image(
                                    painterResource(Res.drawable.feed_avatar_reply),
                                    contentDescription = post.tag,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = OgtColors.muted, fontSize = 12.sp)
    }
}

private fun neighborAvatar(userId: String): DrawableResource = when (userId) {
    OgtIds.Roberto -> Res.drawable.feed_avatar_roberto
    OgtIds.Sofia, OgtIds.Camila -> Res.drawable.feed_avatar_sofia
    OgtIds.CarlosG, OgtIds.CarlosR, OgtIds.DiegoF -> Res.drawable.feed_avatar_carlos
    OgtIds.Mariana, OgtIds.Lucia, OgtIds.MarianaD -> Res.drawable.feed_avatar_mariana
    OgtIds.Lucas -> Res.drawable.feed_avatar_me
    else -> Res.drawable.feed_avatar_reply
}
