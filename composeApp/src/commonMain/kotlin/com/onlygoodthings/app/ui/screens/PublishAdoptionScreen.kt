package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.announcePublishedPost
import com.onlygoodthings.app.data.persistAnimalOnServer
import kotlinx.coroutines.launch
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.domain.titleCasePersonName

/**
 * Ficha para publicar un animal en adopción. No es un aviso de perdido:
 * pide lo que una familia necesita para postular.
 */
@Composable
fun PublishAdoptionScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit = onBack,
    editPostId: String? = null,
) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val me = session.me()
    val auth = LocalAuth.current
    val copy = LocalOgtCopy.current
    val scope = rememberCoroutineScope()
    val editing = !editPostId.isNullOrBlank()
    val existing = remember(editPostId) { editPostId?.let { db.post(it) } }
    val listing = remember(editPostId) {
        existing?.listingId?.let { id -> db.animals.firstOrNull { it.id == id } }
    }
    var name by remember { mutableStateOf(listing?.petName.orEmpty()) }
    var species by remember { mutableStateOf(listing?.species.orEmpty()) }
    var size by remember { mutableStateOf(listing?.size.orEmpty()) }
    var sex by remember { mutableStateOf(listing?.sex.orEmpty()) }
    var age by remember { mutableStateOf(listing?.ageLabel.orEmpty()) }
    var temperament by remember { mutableStateOf(listing?.temperament.orEmpty()) }
    var story by remember { mutableStateOf(listing?.description ?: existing?.body.orEmpty()) }
    var place by remember { mutableStateOf(listing?.place?.ifBlank { me.barrio } ?: me.barrio) }
    var homeNeeds by remember { mutableStateOf(listing?.homeNeeds.orEmpty()) }
    var vaccinated by remember { mutableStateOf(listing?.vaccinated == true) }
    var sterilized by remember { mutableStateOf(listing?.sterilized == true) }
    val draftMedia = rememberAnimalDraftMedia(
        existing?.let { db.mediaOf(it.id).map { media -> media.toDraft() } }.orEmpty(),
    )
    var triedPublish by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val missing = buildList {
        if (draftMedia.isEmpty()) add("una foto")
        if (name.isBlank()) add("el nombre")
        if (species.isBlank()) add("la especie")
        if (size.isBlank()) add("el tamaño")
        if (age.isBlank()) add("la edad")
        if (temperament.isBlank()) add("el carácter")
        if (story.isBlank()) add("su historia")
        if (place.isBlank()) add("el barrio")
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = if (editing) copy.petsEdit else copy.petsAdoption, onBack = onBack)
        ScreenColumn {
            OgtCaption("Estos datos van a la ficha de adopción. El horario de visita se habla después, con quien postula.")
            Text("1. Foto", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            AnimalMediaBlock(draftMedia, missing = triedPublish && draftMedia.isEmpty())
            Text("2. Nombre", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = titleCasePersonName(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Luna", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && name.isBlank()),
            )
            Text("3. Especie, tamaño y sexo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            AnimalChoicePills(AnimalSpecies, species, { species = it }, missing = triedPublish && species.isBlank())
            AnimalChoicePills(AnimalSizes, size, { size = it }, missing = triedPublish && size.isBlank())
            AnimalChoicePills(AnimalSexes, sex, { sex = it })
            Text("4. Edad", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = age,
                onValueChange = { if (it.length <= 30) age = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("6 meses, 3 años…", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && age.isBlank()),
            )
            Text("5. Carácter", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = temperament,
                onValueChange = { if (it.length <= 120) temperament = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cariñosa, sociable, un poco miedosa con ruidos", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && temperament.isBlank()),
            )
            Text("6. Historia y cuidados", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = story,
                onValueChange = { if (it.length <= 400) story = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("De dónde viene, cómo convive, qué come.", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && story.isBlank()),
            )
            Text("7. Salud", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxToggle("Vacunada", vaccinated) { vaccinated = it }
                BoxToggle("Castrada", sterilized) { sterilized = it }
            }
            Text("8. Barrio", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = place,
                onValueChange = { if (it.length <= 60) place = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Parque Centenario", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && place.isBlank()),
            )
            Text("9. Qué hogar necesita", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = homeNeeds,
                onValueChange = { if (it.length <= 160) homeNeeds = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("Patio, sin gatos, alguien en casa varias horas…", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(),
            )
            if (triedPublish && missing.isNotEmpty()) {
                Text("Falta ${joinMissing(missing)}.", color = OgtColors.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            OgtPrimaryButton(
                text = when {
                    saving && editing -> "Guardando…"
                    saving -> "Publicando…"
                    editing -> copy.petsSaveAdoption
                    else -> "Publicar en adopción"
                },
                enabled = !saving,
            ) {
                if (missing.isNotEmpty()) {
                    triedPublish = true
                    return@OgtPrimaryButton
                }
                saving = true
                val post = if (editing && existing != null) {
                    db.updateAnimalListing(
                        author = me,
                        postId = existing.id,
                        petName = name,
                        species = species,
                        size = size,
                        description = story,
                        place = place,
                        ageLabel = age,
                        sex = sex,
                        temperament = temperament,
                        vaccinated = vaccinated,
                        sterilized = sterilized,
                        homeNeeds = homeNeeds,
                    )
                } else {
                    db.publishAnimalListing(
                        author = me,
                        kind = "ADOPTION",
                        petName = name,
                        species = species,
                        size = size,
                        description = story,
                        place = place,
                        ageLabel = age,
                        sex = sex,
                        temperament = temperament,
                        vaccinated = vaccinated,
                        sterilized = sterilized,
                        homeNeeds = homeNeeds,
                    )
                } ?: run {
                    saving = false
                    return@OgtPrimaryButton
                }
                if (editing) {
                    db.replaceAnimalMedia(post.id, "Adopción", draftMedia)
                } else {
                    db.attachAnimalMedia(post.id, "Adopción", draftMedia)
                }
                session.persistPublishedAnimals()
                auth.ensureDevBearer()
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        val live = runCatching {
                            persistAnimalOnServer(db, auth.animals, me, post, draftMedia, session.here())
                        }.getOrDefault(post)
                        session.persistPublishedAnimals()
                        if (!editing) announcePublishedPost(db, live)
                    }
                    onPublished()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BoxToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.clickable { onChange(!on) }) {
        OgtPill(
            label,
            if (on) OgtColors.secondary else OgtColors.sand,
            if (on) OgtColors.onPrimary else OgtColors.ink,
        )
    }
}
