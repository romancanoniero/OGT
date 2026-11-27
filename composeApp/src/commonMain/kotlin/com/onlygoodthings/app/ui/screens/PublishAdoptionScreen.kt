package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.*
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*
import com.onlygoodthings.shared.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One adoption publication may introduce one animal or a group. Historical traits remain stored. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublishAdoptionScreen(onBack: () -> Unit, onPublished: () -> Unit = onBack, editPostId: String? = null) {
    val db = LocalOgtDb.current; val session = LocalOgtSession.current; val auth = LocalAuth.current
    val me = session.me(); val copy = LocalOgtCopy.current; val scope = rememberCoroutineScope()
    val existing = remember(editPostId) { editPostId?.let(db::post) }
    val listing = remember(editPostId) { existing?.listingId?.let { id -> db.animals.firstOrNull { it.id == id } } }
    val editing = editPostId != null
    var title by remember(editPostId) { mutableStateOf(listing?.displayPetName().orEmpty()) }
    var presentation by remember(editPostId) { mutableStateOf(buildString {
        append(listing?.adoptionDetails?.presentation?.takeIf { it.isNotBlank() } ?: listing?.description ?: existing?.body.orEmpty())
        if (listing?.adoptionDetails?.presentation.isNullOrBlank() && !listing?.temperament.isNullOrBlank()) {
            append("\n\nCarácter: "); append(listing?.temperament)
        }
    }) }
    var age by remember(editPostId) { mutableStateOf(listing?.ageLabel.orEmpty()) }
    var history by remember(editPostId) { mutableStateOf(listing?.adoptionDetails?.historyAndCare.orEmpty()) }
    var place by remember(editPostId) { mutableStateOf(listing?.place?.ifBlank { me.barrio } ?: me.barrio) }
    var home by remember(editPostId) { mutableStateOf(listing?.homeNeeds.orEmpty()) }
    var mode by remember(editPostId) { mutableStateOf(listing?.adoptionDetails?.adoptionMode ?: "UNSPECIFIED") }
    var count by remember(editPostId) { mutableStateOf(listing?.adoptionDetails?.availableCount?.toString().orEmpty()) }
    var attribution by remember(editPostId) { mutableStateOf(existing?.attributionUrl.orEmpty()) }
    var selected by remember(editPostId) { mutableStateOf(listing?.adoptionDetails?.tags.orEmpty()) }
    var free by remember(editPostId) { mutableStateOf(listing?.adoptionDetails?.freeHashtags.orEmpty()) }
    var freeInput by remember(editPostId) { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<AdoptionTag>?>(null) }
    var initialized by remember(editPostId) { mutableStateOf(listing?.adoptionDetails != null) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    var pendingPostId by remember(editPostId) { mutableStateOf(editPostId) }
    val media = rememberAnimalDraftMedia(existing?.let { db.mediaOf(it.id).map { row -> row.toDraft() } }.orEmpty())

    LaunchedEffect(auth, retry) {
        catalogError = null
        try {
            val loaded = auth.animals.adoptionTags()
            catalog = loaded
            if (!initialized) {
                selected = listing?.let { AdoptionRules.legacyTags(loaded, it.species, it.size, it.sex, it.vaccinated, it.sterilized) }.orEmpty()
                initialized = true
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { catalogError = "No pudimos cargar las etiquetas. Reintentá." }
    }
    fun select(tag: AdoptionTag) {
        val next = AdoptionRules.toggle(selected, tag)
        if (next.size + free.size > AdoptionRules.MAX_TAGS) error = "Podés elegir hasta 24 etiquetas." else { selected = next; error = null }
    }
    fun addFree() {
        if (freeInput.isBlank()) return
        val tag = Hashtags.free(freeInput)
        val official = catalog.orEmpty().firstOrNull { it.key == tag.key || Hashtags.key(it.label) == tag.key }
        if (official != null) {
            require(official.active || selected.any { it.id == official.id }) { "Esa etiqueta fue desactivada en el CRM." }
            if (selected.none { it.id == official.id }) select(official)
        } else if (free.none { Hashtags.key(it) == tag.key }) {
            require(free.size < 6 && selected.size + free.size < AdoptionRules.MAX_TAGS) { "Podés agregar hasta seis hashtags libres." }
            free = free + tag.label
        }
        freeInput = ""
    }
    fun missing() = buildList {
        if (media.isEmpty()) add("una foto o video")
        if (title.isBlank()) add("el título")
        if (presentation.isBlank()) add("la presentación")
        if (selected.none { it.group == "SPECIES" }) add("la especie")
        if (place.isBlank()) add("el barrio o localidad")
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = if (editing) "Editar adopción" else copy.petsAdoption, onBack = onBack)
        ScreenColumn {
            AnimalMediaBlock(media, missing = tried && media.isEmpty(), reorderEnabled = !saving)
            OgtOutlinedTextField(title, { if (it.length <= AdoptionRules.MAX_TITLE) title = it },
                Modifier.fillMaxWidth(), label = { Text("Título del posteo") }, enabled = !saving,
                placeholder = { Text("Tres hermanitos buscan hogar") }, singleLine = true, isError = tried && title.isBlank())
            OgtOutlinedTextField(presentation, { if (it.length <= AdoptionRules.MAX_PRESENTATION) presentation = it },
                Modifier.fillMaxWidth(), label = { Text("Presentación") }, enabled = !saving, minLines = 3,
                placeholder = { Text("Presentá a los animales y contá cómo son.") }, isError = tried && presentation.isBlank())
            Text("Etiquetas", fontWeight = FontWeight.SemiBold)
            catalogError?.let { message -> Text(message, color = OgtColors.error); TextButton(onClick = { retry++ }) { Text("Reintentar etiquetas") } }
            val options = (catalog.orEmpty().filter { it.active } + selected).distinctBy { it.id }
            for (group in AdoptionRules.groups) {
                val rows = options.filter { it.group == group }
                if (rows.isNotEmpty()) {
                    OgtCaption(AdoptionRules.groupLabel(group))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { tag ->
                            FilterChip(selected = selected.any { it.id == tag.id }, enabled = !saving,
                                onClick = { select(tag) }, label = { Text(PostHashtag(tag.key, tag.label).display) })
                        }
                    }
                }
            }
            OgtOutlinedTextField(freeInput, { if (it.length <= 60) freeInput = it }, Modifier.fillMaxWidth(),
                label = { Text("Otro hashtag (opcional)") }, enabled = !saving, singleLine = true)
            TextButton(onClick = { try { addFree() } catch (e: Exception) { error = e.message } }, enabled = !saving) { Text("Agregar hashtag") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                free.forEach { tag -> FilterChip(selected = true, onClick = { free = free - tag }, enabled = !saving,
                    label = { Text("#${tag} ×") }) }
            }
            OgtOutlinedTextField(age, { if (it.length <= 30) age = it }, Modifier.fillMaxWidth(),
                label = { Text("Edad (opcional)") }, placeholder = { Text("3 meses, entre 1 y 2 años…") }, enabled = !saving)
            OgtOutlinedTextField(history, { if (it.length <= AdoptionRules.MAX_HISTORY) history = it }, Modifier.fillMaxWidth(),
                label = { Text("Historia y cuidados (opcional)") }, enabled = !saving, minLines = 2)
            PublicationPlaceField(place, { place = it }, label = "Barrio o localidad", enabled = !saving)
            OgtOutlinedTextField(home, { if (it.length <= 160) home = it }, Modifier.fillMaxWidth(),
                label = { Text("Qué hogar necesita") }, enabled = !saving, minLines = 2,
                placeholder = { Text("Patio, convivencia con otros animales, tiempo en casa…") })
            Text("Cómo se pueden adoptar", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("UNSPECIFIED" to "No indicado", "SEPARATE" to "Por separado", "TOGETHER" to "Juntos").forEach { (code, label) ->
                    FilterChip(selected = mode == code, onClick = { mode = code }, enabled = !saving, label = { Text(label) })
                }
            }
            OgtOutlinedTextField(count, { if (it.length <= 3 && it.all(Char::isDigit)) count = it }, Modifier.fillMaxWidth(),
                label = { Text("Cantidad que sigue buscando hogar (opcional)") }, enabled = !saving, singleLine = true)
            AttributionField(attribution, { attribution = it }, enabled = !saving, label = "Fuentes y créditos (opcional)")
            if (tried && missing().isNotEmpty()) Text("Falta ${joinMissing(missing())}.", color = OgtColors.error)
            error?.let { Text(it, color = OgtColors.error) }
            OgtPrimaryButton(if (saving) "Guardando…" else if (editing) copy.petsSaveAdoption else "Publicar en adopción", enabled = !saving) {
                try {
                    addFree()
                    if (missing().isNotEmpty()) { tried = true; return@OgtPrimaryButton }
                    val loaded = requireNotNull(catalog) { "Reintentá cargar las etiquetas antes de publicar." }
                    require(count.isBlank() || count.toIntOrNull()?.let { it in 1..100 } == true) { "La cantidad disponible debe estar entre 1 y 100." }
                    val details = AdoptionRules.normalize(AdoptionDetails(presentation = presentation, historyAndCare = history, tags = selected, freeHashtags = free,
                        adoptionMode = mode, availableCount = count.toIntOrNull()), loaded, selected.filterNot { it.active }.map { it.id }.toSet())
                    val credit = ContentAttribution.normalize(attribution).orEmpty()
                    val species = details.tags.first { it.group == "SPECIES" }.legacyValue.ifBlank { "OTHER" }
                    val size = AdoptionRules.singleValue(details, "SIZE")
                    val sex = AdoptionRules.singleValue(details, "SEX")
                    val vaccinated = AdoptionRules.singleValue(details, "VACCINATION") == "ALL"
                    val sterilized = AdoptionRules.singleValue(details, "STERILIZATION") == "ALL"
                    val name = listing?.petName?.ifBlank { null } ?: title.take(40)
                    val post = if (pendingPostId != null) db.updateAnimalListing(me, pendingPostId!!, name, species, size, presentation, place,
                        ageLabel = age, sex = sex, temperament = listing?.temperament.orEmpty(), vaccinated = vaccinated, sterilized = sterilized,
                        homeNeeds = home, title = title, adoptionDetails = details, attributionUrl = credit)
                    else db.publishAnimalListing(me, "ADOPTION", name, species, size, presentation, place,
                        ageLabel = age, sex = sex, vaccinated = vaccinated, sterilized = sterilized,
                        homeNeeds = home, title = title, adoptionDetails = details, attributionUrl = credit)
                    requireNotNull(post) { "Revisá los datos de la adopción." }
                    pendingPostId = post.id; saving = true; error = null
                    if (db.mediaOf(post.id).isNotEmpty()) db.replaceAnimalMedia(post.id, "Adopción", media) else db.attachAnimalMedia(post.id, "Adopción", media)
                    session.persistPublishedAnimals(); auth.ensureDevBearer()
                    scope.launch {
                        try {
                            withContext(NonCancellable) {
                                val live = persistAnimalOnServer(db, auth.animals, me, post, media, session.here(), { session.currentUserId == me.id }, auth::uploadPostMedia)
                                session.persistPublishedAnimals(); if (!editing) announcePublishedPost(db, live)
                            }
                            onPublished()
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (e: Exception) { error = e.message ?: "No se pudo guardar. Reintentá." }
                        finally { saving = false }
                    }
                } catch (e: Exception) { error = e.message }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
