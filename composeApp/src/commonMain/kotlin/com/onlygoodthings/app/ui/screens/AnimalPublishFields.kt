package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtDraftMediaStrip
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaRules

internal val AnimalSpecies = listOf(
    "DOG" to "Perro",
    "CAT" to "Gato",
    "BIRD" to "Ave",
    "RABBIT" to "Conejo",
    "HAMSTER" to "Hámster",
    "FISH" to "Pez",
    "TURTLE" to "Tortuga",
    "OTHER" to "Otro",
)
internal val AnimalSizes = listOf("SMALL" to "Chico", "MEDIUM" to "Mediano", "LARGE" to "Grande")
internal val AnimalSexes = listOf("HEMBRA" to "Hembra", "MACHO" to "Macho")

internal fun animalSpeciesLabel(code: String): String? =
    AnimalSpecies.firstOrNull { it.first == code }?.second ?: code.takeIf { it.isNotBlank() }

internal fun animalSizeLabel(code: String?): String? =
    AnimalSizes.firstOrNull { it.first == code }?.second?.lowercase()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimalChoicePills(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    missing: Boolean = false,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(Modifier.clickable { onSelect(id) }) {
                OgtPill(
                    label,
                    when {
                        on -> OgtColors.secondary
                        missing -> OgtColors.errorContainer
                        else -> OgtColors.sand
                    },
                    when {
                        on -> OgtColors.onPrimary
                        missing -> OgtColors.onErrorContainer
                        else -> OgtColors.ink
                    },
                )
            }
        }
    }
}

@Composable
internal fun rememberAnimalDraftMedia(seed: List<OgtPickedMedia> = emptyList()): MutableList<OgtPickedMedia> =
    remember { mutableStateListOf<OgtPickedMedia>().also { it.addAll(seed) } }

internal fun LocalPostMedia.toDraft(): OgtPickedMedia {
    val fromDevice = url.startsWith("file://")
    val path = when {
        fromDevice -> url.removePrefix("file://")
        url.startsWith("asset://") -> url.removePrefix("asset://")
        else -> assetKey.ifBlank { url }
    }
    return OgtPickedMedia(
        id = id,
        kind = kind,
        path = path,
        fromDevice = fromDevice,
        posterPath = posterUrl?.removePrefix("file://"),
    )
}

@Composable
internal fun AnimalMediaBlock(
    draftMedia: MutableList<OgtPickedMedia>,
    missing: Boolean,
) {
    OgtDraftMediaStrip(
        items = draftMedia,
        missing = missing,
        heroDescription = "Foto de la mascota",
    )
}

internal fun OgtLocalDatabase.attachAnimalMedia(
    postId: String,
    tag: String,
    draftMedia: List<OgtPickedMedia>,
) {
    draftMedia.take(PostMediaRules.MAX_ITEMS).forEachIndexed { order, item ->
        val video = item.kind == MediaKind.VIDEO
        val url = if (item.fromDevice) "file://${item.path}" else "asset://${item.path}"
        val poster = item.posterPath?.let { "file://$it" }
        postMedia += LocalPostMedia(
            id = "$postId-m$order",
            postId = postId,
            kind = item.kind,
            url = url,
            posterUrl = poster,
            sortOrder = order,
            assetKey = if (item.fromDevice) "" else if (video) "feed_story_playa" else item.path,
            altText = tag,
        )
        if (item.fromDevice) {
            OgtMediaCache.ingest(url)
            poster?.let { OgtMediaCache.ingest(it) }
        }
    }
}

internal fun OgtLocalDatabase.replaceAnimalMedia(
    postId: String,
    tag: String,
    draftMedia: List<OgtPickedMedia>,
) {
    postMedia.removeAll { it.postId == postId }
    attachAnimalMedia(postId, tag, draftMedia)
    bumpFeed()
    bumpSocial()
}

internal fun joinMissing(bits: List<String>): String = when (bits.size) {
    0 -> ""
    1 -> bits[0]
    2 -> "${bits[0]} y ${bits[1]}"
    else -> bits.dropLast(1).joinToString(", ") + " y " + bits.last()
}
