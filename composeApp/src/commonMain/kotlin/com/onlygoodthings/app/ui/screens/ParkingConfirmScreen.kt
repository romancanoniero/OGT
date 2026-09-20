package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.announcePublishedPost
import kotlinx.coroutines.launch
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.parking_action_parked
import com.onlygoodthings.app.resources.parking_action_yield
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.data.local.LocalPostPerson
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.realtime.currentEpochMs

@Composable
fun ParkingConfirmScreen(spotId: String, onDone: () -> Unit) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val copy = LocalOgtCopy.current
    val me = session.me()
    val local = db.parkingSpots.firstOrNull { it.id == spotId }
    val handoff = remember(spotId, db.parkingEpoch) {
        db.parkingHandoffs.lastOrNull { it.parkingSpotId == spotId }
    }
    val owner = db.userOrNull(local?.ownerUserId ?: handoff?.ownerUserId)
    val claimant = db.userOrNull(local?.claimedByUserId ?: handoff?.claimantUserId)
    val iClaimed = me.id == (handoff?.claimantUserId ?: local?.claimedByUserId)
    val points = handoff?.pointsAwarded ?: local?.rewardPoints ?: ParkingRules.DEFAULT_REWARD_POINTS
    val address = local?.address ?: copy.plaza
    var shared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun shareImpact() {
        if (shared) {
            onDone()
            return
        }
        val now = currentEpochMs()
        val postId = "post-park-$now"
        val neighbor = if (iClaimed) owner?.displayName ?: copy.neighbor else claimant?.displayName ?: copy.neighbor
        db.posts += LocalSocialPost(
            id = postId,
            authorKind = AuthorKind.USER,
            authorUserId = me.id,
            authorCompanyId = null,
            place = address,
            timeLabel = "Ahora",
            tag = "Parking colaborativo",
            body = if (iClaimed) copy.handoffShareClaimant(address, neighbor) else copy.handoffShareOwner(address, neighbor),
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = now,
        )
        db.postPeople += LocalPostPerson(postId, me.id, PostPersonRole.AUTHOR)
        db.postMedia += LocalPostMedia(
            id = "$postId-m0",
            postId = postId,
            kind = MediaKind.IMAGE,
            url = "asset://feed_photo_arboles",
            sortOrder = 0,
            assetKey = "arboles",
            altText = "Cesión de parking",
        )
        db.recordFeedEvent(me.id, postId, FeedEventKind.SHARE)
        db.bumpFeed()
        scope.launch { announcePublishedPost(db, db.post(postId)) }
        val idx = db.users.indexOfFirst { it.id == me.id }
        if (idx >= 0) {
            val user = db.users[idx]
            db.users[idx] = user.copy(communityPoints = user.communityPoints + ParkingRules.SHARE_BONUS_POINTS)
        }
        shared = true
    }

    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = if (iClaimed) copy.handoffClaimantTitle else copy.handoffOwnerTitle, onBack = onDone)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        OgtPill(if (iClaimed) "$address · ${copy.leftItHere}" else "$address · ${copy.yieldInProgress}")
        Box(Modifier.size(88.dp).clip(CircleShape).background(OgtColors.mint), contentAlignment = Alignment.Center) {
            OgtStitchIcon(
                if (iClaimed) Res.drawable.parking_action_parked else Res.drawable.parking_action_yield,
                if (iClaimed) copy.leftItHere else copy.yieldMySpot,
                size = 64.dp,
                tint = null,
            )
        }
        OgtSectionTitle(if (iClaimed) copy.handoffClaimantTitle else copy.handoffOwnerTitle)
        OgtCaption(if (iClaimed) copy.handoffClaimantBody else copy.handoffOwnerBody)
        OgtCard {
            Text(
                if (iClaimed) owner?.displayName ?: copy.neighbor else claimant?.displayName ?: copy.neighbor,
                fontWeight = FontWeight.Bold,
            )
            OgtCaption(
                if (iClaimed) session.parkedCar?.vehicle?.label() ?: session.vehicle.takeIf { it.isReady() }?.label() ?: copy.yourCar
                else local?.vehicleLabel ?: copy.nearbySpot,
            )
            OgtCaption("$address · ${handoff?.proximityMeters?.toInt() ?: ParkingRules.PROXIMITY_METERS.toInt()} m")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OgtPill("+$points Pts")
            OgtPill("${ParkingRules.CO2_GRAMS_PER_HANDOFF / 1000.0} kg de CO₂ no emitidos")
            OgtPill(me.levelLabel, OgtColors.sunset, OgtColors.sunsetText)
        }
        OgtPrimaryButton(
            if (shared) {
                if (iClaimed) copy.backToParked else copy.backToRadar
            } else {
                "Compartir mi impacto en el Feed (+${ParkingRules.SHARE_BONUS_POINTS} pts extra)"
            },
        ) { shareImpact() }
        OgtSecondaryButton(if (iClaimed) copy.backToParked else copy.backToRadar) { onDone() }
        Spacer(Modifier.height(16.dp))
        }
    }
}
