package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.onlygoodthings.app.ui.components.OgtPlaceSearchField
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.OgtCrmDefaults
import com.onlygoodthings.shared.domain.titleCasePersonName

/**
 * Ficha de mascota perdida. Los campos alimentan la card: nombre, especie/tamaño,
 * señas, última vista y foto. El radio lo define el CRM.
 */
@Composable
fun ReportAnimalScreen(onDone: () -> Unit) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val me = session.me()
    val auth = LocalAuth.current
    val copy = LocalOgtCopy.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf("") }
    var lastSeen by remember { mutableStateOf("") }
    var lastSeenPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var lastSeenLabel by remember { mutableStateOf("") }
    var story by remember { mutableStateOf("") }
    val draftMedia = rememberAnimalDraftMedia()
    var triedPublish by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val missing = buildList {
        if (draftMedia.isEmpty()) add("una foto")
        if (name.isBlank()) add("el nombre")
        if (species.isBlank()) add("la especie")
        if (size.isBlank()) add("el tamaño")
        if (marks.isBlank()) add("las señas")
        if (lastSeen.isBlank() || lastSeenPoint == null) add("dónde se lo vio")
        if (story.isBlank()) add("cómo es")
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.petsLost, onBack = onDone)
        ScreenColumn {
            OgtCaption("Lo que complete acá es lo que ve la comunidad en la alerta: señas y última vista. El radio lo define el CRM.")
            Text("1. Foto clara", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            AnimalMediaBlock(draftMedia, missing = triedPublish && draftMedia.isEmpty())
            Text("2. ¿Cómo se llama?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = titleCasePersonName(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Oliver", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && name.isBlank()),
            )
            Text("3. Especie y tamaño", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            AnimalChoicePills(AnimalSpecies, species, { species = it }, missing = triedPublish && species.isBlank())
            AnimalChoicePills(AnimalSizes, size, { size = it }, missing = triedPublish && size.isBlank())
            Text("4. Señas particulares", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OgtCaption("Collar, mancha, oreja, paso. Es lo que se lee bajo “Última vista” en la card.")
            OutlinedTextField(
                value = marks,
                onValueChange = { if (it.length <= 160) marks = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("Collar rojo, oreja derecha caída, paso alegre", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && marks.isBlank()),
            )
            Text("5. Última vista", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OgtCaption("Buscá la calle. El pin de ubicación marca dónde estás; el mapa deja ajustar el punto.")
            OgtPlaceSearchField(
                query = lastSeen,
                onQueryChange = {
                    lastSeen = it
                    if (it.trim() != lastSeenLabel) lastSeenPoint = null
                },
                point = lastSeenPoint,
                onResolved = { hit ->
                    lastSeen = hit.label
                    lastSeenLabel = hit.label
                    lastSeenPoint = hit.point
                },
                missing = triedPublish && (lastSeen.isBlank() || lastSeenPoint == null),
            )
            Text("6. Cómo es y qué hacer si lo ven", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = story,
                onValueChange = { if (it.length <= 400) story = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Mestizo grande. Responde a su nombre. No lo persigan.", color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = triedPublish && story.isBlank()),
            )
            if (triedPublish && missing.isNotEmpty()) {
                Text("Falta ${joinMissing(missing)}.", color = OgtColors.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            OgtPrimaryButton(if (saving) "Publicando…" else "Emitir alerta a la comunidad", enabled = !saving) {
                if (missing.isNotEmpty()) {
                    triedPublish = true
                    return@OgtPrimaryButton
                }
                saving = true
                val place = lastSeenPoint ?: return@OgtPrimaryButton
                val post = db.publishAnimalListing(
                    author = me,
                    kind = "LOST",
                    petName = name,
                    species = species,
                    size = size,
                    description = story,
                    place = lastSeen,
                    marks = marks,
                    lastSeenPlace = lastSeen,
                    alertRadiusM = OgtCrmDefaults.LOST_ALERT_RADIUS_M,
                    latitude = place.latitude,
                    longitude = place.longitude,
                ) ?: run {
                    saving = false
                    return@OgtPrimaryButton
                }
                db.attachAnimalMedia(post.id, "Mascota perdida", draftMedia)
                session.persistPublishedAnimals()
                auth.ensureDevBearer()
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        val live = runCatching {
                            persistAnimalOnServer(db, auth.animals, me, post, draftMedia, place)
                        }.getOrDefault(post)
                        session.persistPublishedAnimals()
                        announcePublishedPost(db, live)
                    }
                    onDone()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
