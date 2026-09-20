package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_feed
import com.onlygoodthings.app.resources.qs_gps
import com.onlygoodthings.app.resources.qs_parked
import com.onlygoodthings.app.resources.qs_paw
import com.onlygoodthings.app.resources.qs_search
import com.onlygoodthings.app.resources.qs_skills
import com.onlygoodthings.app.resources.qs_wallet
import com.onlygoodthings.app.resources.qs_yield
import com.onlygoodthings.app.resources.feed_avatar_carlos
import com.onlygoodthings.app.resources.feed_avatar_mariana
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_avatar_reply
import com.onlygoodthings.app.resources.feed_avatar_roberto
import com.onlygoodthings.app.resources.feed_avatar_sofia
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.data.local.OgtIds
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Icono de producto Stitch Quiet Studio (SVG → vector).
 * Los tiles 3D (parking_action_*, onboard_*) siguen para héroes grandes.
 */
@Composable
fun OgtStitchIcon(
    art: DrawableResource,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color? = OgtColors.ink,
) {
    Image(
        painter = painterResource(art),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

fun tabStitchArt(route: OgtRoute): DrawableResource = when (route) {
    OgtRoute.Feed -> Res.drawable.qs_feed
    OgtRoute.Parking -> Res.drawable.qs_search
    OgtRoute.Animals -> Res.drawable.qs_paw
    OgtRoute.Skills -> Res.drawable.qs_skills
    OgtRoute.Settings -> Res.drawable.qs_wallet
    else -> Res.drawable.qs_feed
}

fun pinStitchArt(kind: String): DrawableResource = when (kind) {
    "CAR" -> Res.drawable.qs_parked
    "PARKING" -> Res.drawable.qs_search
    "ANIMAL" -> Res.drawable.qs_paw
    "ORIGIN" -> Res.drawable.qs_search
    "SIGHT" -> Res.drawable.qs_gps
    "SKILL" -> Res.drawable.qs_skills
    "ME" -> Res.drawable.qs_gps
    else -> Res.drawable.qs_yield
}

fun neighborAvatarArt(userId: String): DrawableResource = when (userId) {
    OgtIds.Roberto -> Res.drawable.feed_avatar_roberto
    OgtIds.Sofia, OgtIds.Camila -> Res.drawable.feed_avatar_sofia
    OgtIds.CarlosG, OgtIds.CarlosR, OgtIds.DiegoF -> Res.drawable.feed_avatar_carlos
    OgtIds.Mariana, OgtIds.Lucia, OgtIds.MarianaD, OgtIds.Valeria, OgtIds.ValeriaP -> Res.drawable.feed_avatar_mariana
    OgtIds.Lucas -> Res.drawable.feed_avatar_me
    else -> Res.drawable.feed_avatar_reply
}
