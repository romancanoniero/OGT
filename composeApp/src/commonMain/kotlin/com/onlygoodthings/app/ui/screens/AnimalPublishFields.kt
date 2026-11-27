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
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.domain.AdoptionRules
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.isUuid
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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

internal fun LocalAnimalListing.adoptionPresentation(): String =
    adoptionDetails?.presentation?.takeIf { it.isNotBlank() } ?: description

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
    reorderEnabled: Boolean = true,
) {
    OgtDraftMediaStrip(
        items = draftMedia,
        missing = missing,
        heroDescription = "Foto de la mascota",
        reorderEnabled = reorderEnabled,
    )
}

internal fun OgtLocalDatabase.attachAnimalMedia(
    postId: String,
    tag: String,
    draftMedia: List<OgtPickedMedia>,
    previous: List<LocalPostMedia> = emptyList(),
) {
    val retainedIds = mutableSetOf<String>()
    draftMedia.take(PostMediaRules.MAX_ITEMS).forEachIndexed { order, item ->
        val video = item.kind == MediaKind.VIDEO
        val remote = !item.fromDevice && (item.path.startsWith("https://") || item.path.startsWith("http://"))
        val url = if (remote) item.path else if (item.fromDevice) "file://${item.path}" else "asset://${item.path}"
        val poster = item.posterPath?.let { if (remote) it else "file://$it" }
        val retained = previous.firstOrNull { it.id !in retainedIds && (it.id == item.id || it.url == url) }
        retained?.let { retainedIds += it.id }
        val fresh = LocalPostMedia(
            id = "$postId-m$order",
            postId = postId,
            kind = item.kind,
            url = url,
            posterUrl = poster,
            sortOrder = order,
            assetKey = if (item.fromDevice || video || remote) "" else item.path,
            altText = tag,
        )
        postMedia += retained?.copy(url = url, posterUrl = poster ?: retained.posterUrl, sortOrder = order) ?: fresh
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
    val previous = postMedia.filter { it.postId == postId }
    postMedia.removeAll { it.postId == postId }
    attachAnimalMedia(postId, tag, draftMedia, previous)
    bumpFeed()
    bumpSocial()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdoptionSummary(listing: LocalAnimalListing, detailed: Boolean = false) {
    if (listing.resolved) OgtCaption("Adopción cerrada")
    val details = listing.adoptionDetails ?: return
    if (listing.ageLabel.isNotBlank()) OgtCaption(listing.ageLabel)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AdoptionRules.hashtags(details).filterNot { it.key == "adopcion" }.forEach { tag ->
            OgtPill(tag.display, OgtColors.sand, OgtColors.ink)
        }
    }
    AdoptionRules.modeLabel(details.adoptionMode).takeIf { it.isNotBlank() }?.let { OgtCaption(it) }
    if (!listing.resolved) details.availableCount?.let { OgtCaption(if (it == 1) "Queda 1 buscando hogar" else "Quedan $it buscando hogar") }
    if (detailed && details.historyAndCare.isNotBlank()) OgtCaption("Historia y cuidados · ${details.historyAndCare}")
}

@Composable
internal fun CloseAdoptionControl(listing: LocalAnimalListing) {
    if (listing.resolved || !listing.id.isUuid()) return
    val auth = LocalAuth.current; val db = LocalOgtDb.current; val session = LocalOgtSession.current
    val scope = rememberCoroutineScope()
    var confirm by remember(listing.id) { mutableStateOf(false) }
    var busy by remember(listing.id) { mutableStateOf(false) }
    var error by remember(listing.id) { mutableStateOf<String?>(null) }
    TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Todos encontraron hogar") }
    error?.let { Text(it, color = OgtColors.error) }
    if (confirm) AlertDialog(
        onDismissRequest = { if (!busy) confirm = false },
        title = { Text("¿Todos encontraron hogar?") },
        text = { androidx.compose.foundation.layout.Column {
            Text("Se cerrará el aviso de adopción para todos los animales de esta publicación.")
            error?.let { Text(it, color = OgtColors.error) }
        } },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = !busy) { Text("Cancelar") } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val owner = session.currentUserId
            busy = true; error = null
            scope.launch {
                try {
                    check(session.currentUserId == owner) { "La sesión cambió. Volvé a intentarlo." }
                    auth.animals.resolve(listing.id)
                    val updated = auth.animals.getListing(listing.id)
                    check(session.currentUserId == owner) { "La sesión cambió. Volvé a intentarlo." }
                    db.upsertRemoteAnimal(updated); session.persistPublishedAnimals(); confirm = false
                } catch (cancel: CancellationException) { throw cancel }
                catch (e: Exception) { error = e.message ?: "No se pudo cerrar la adopción. Reintentá." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Cerrando…" else "Confirmar cierre") } },
    )
}

internal fun joinMissing(bits: List<String>): String = when (bits.size) {
    0 -> ""
    1 -> bits[0]
    2 -> "${bits[0]} y ${bits[1]}"
    else -> bits.dropLast(1).joinToString(", ") + " y " + bits.last()
}
