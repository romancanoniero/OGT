package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.publish_photo
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.shared.data.local.LocalAnimalListing

private enum class PetsFilter { ALL, ADOPTION, LOST }

@Composable
fun AnimalsScreen(
    onReportLost: () -> Unit,
    onReportAdopt: () -> Unit,
    onPetsMap: () -> Unit,
    onOpenAdoption: (String) -> Unit = {},
    onEditAdoption: (String) -> Unit = {},
) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val me = session.me()
    val auth = LocalAuth.current
    val copy = LocalOgtCopy.current
    val lostTick = db.lostEpoch
    LaunchedEffect(Unit) {
        auth.hydratePublishedAnimals(db)
        session.persistPublishedAnimals()
    }
    var filter by remember { mutableStateOf(PetsFilter.ALL) }
    val listings = remember(lostTick, filter, me.id) {
        db.animals.filter { !it.resolved }.filter { animal ->
            when (filter) {
                PetsFilter.ALL -> animal.kind == "ADOPTION" || animal.kind == "LOST"
                PetsFilter.ADOPTION -> animal.kind == "ADOPTION"
                PetsFilter.LOST -> animal.kind == "LOST"
            }
        }.sortedWith(
            compareByDescending<LocalAnimalListing> { me.owns(it.reporterUserId) }
                .thenByDescending { it.createdAtEpochMs },
        )
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.petsTitle)
        ScreenColumn {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip("Todas", filter == PetsFilter.ALL) { filter = PetsFilter.ALL }
                FilterChip(copy.petsAdoption, filter == PetsFilter.ADOPTION) { filter = PetsFilter.ADOPTION }
                FilterChip(copy.petsLost, filter == PetsFilter.LOST) { filter = PetsFilter.LOST }
            }
            OgtPrimaryButton(copy.petsLost) { onReportLost() }
            OgtPrimaryButton(copy.petsAdoption) { onReportAdopt() }
            OgtPrimaryButton(copy.petsMapCta) { onPetsMap() }
            listings.forEach { animal ->
                PetListingCard(
                    animal = animal,
                    mine = me.owns(animal.reporterUserId),
                    onMap = onPetsMap,
                    onOpenAdoption = onOpenAdoption,
                    onEditAdoption = onEditAdoption,
                )
            }
            if (listings.isEmpty()) {
                OgtCaption(copy.petsMapHint)
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(76.dp))
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) {
        OgtPill(
            label,
            if (selected) OgtColors.secondary else OgtColors.sand,
            if (selected) androidx.compose.ui.graphics.Color.White else OgtColors.ink,
        )
    }
}

@Composable
private fun PetListingCard(
    animal: LocalAnimalListing,
    mine: Boolean,
    onMap: () -> Unit,
    onOpenAdoption: (String) -> Unit,
    onEditAdoption: (String) -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    val lost = animal.kind == "LOST"
    val postId = remember(animal.id) { db.posts.firstOrNull { it.listingId == animal.id }?.id }
    val cover = remember(animal.id) {
        postId?.let { db.mediaOf(it).firstOrNull() }
    }
    val species = listOfNotNull(
        animalSpeciesLabel(animal.species),
        animalSizeLabel(animal.size),
    ).joinToString(" · ")
    OgtCard {
        OgtPill(
            if (lost) copy.petsLost else copy.petsAdoption,
            if (lost) OgtColors.sunset else OgtColors.sand,
            if (lost) OgtColors.sunsetText else OgtColors.ink,
        )
        if (cover != null) {
            OgtPostImage(
                item = cover,
                fallback = Res.drawable.publish_photo,
                contentDescription = animal.title,
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(OgtColors.sand))
        }
        if (lost) {
            OgtCaption("Alerta · ${animal.place} · ${animal.alertRadiusM} m")
        }
        if (species.isNotBlank()) OgtCaption(species)
        Text(animal.title, fontWeight = FontWeight.Bold)
        OgtCaption(animal.description)
        if (!animal.place.isBlank()) OgtCaption(animal.place)
        if (lost) {
            OgtPrimaryButton(copy.petsMapCta) { onMap() }
        } else if (postId != null) {
            if (mine) {
                OgtPrimaryButton(copy.petsEdit) { onEditAdoption(postId) }
            } else {
                OgtPrimaryButton(copy.petsWantAdopt) { onOpenAdoption(postId) }
            }
        }
    }
}
