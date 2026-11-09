package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.onlygoodthings.app.ui.components.OgtOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.onlygoodthings.app.data.isUuid
import kotlinx.coroutines.launch
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.domain.titleCasePersonName
import kotlinx.coroutines.delay

/**
 * Postulación a adopción. Modelo de refugio: contacto, domicilio, hogar y motivo.
 * Al enviar abre conversación con quien publicó, con el formulario adjunto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdoptApplicationScreen(
    postId: String,
    onBack: () -> Unit,
    onSent: (matchId: String) -> Unit = { onBack() },
) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val me = session.me()
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var sending by remember(me.id,postId) { mutableStateOf(false) }
    var sendError by remember(me.id,postId) { mutableStateOf<String?>(null) }
    val copy = LocalOgtCopy.current
    val es = copy.lang == OgtLang.ES
    val post = db.post(postId)
    val listing = post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } }
    val remote = com.onlygoodthings.app.ui.components.rememberAccountData("${me.id}:$postId") {
        if (listing?.id?.isUuid() == true) auth.account.threads().firstOrNull { it.animalListingId == listing.id && it.animalContactKind == "ADOPTION" } else null
    }
    val existing = remember(postId, me.id) {
        db.adoptRequests.firstOrNull { it.postId == postId && it.applicantUserId == me.id }
    }
    var name by remember { mutableStateOf(titleCasePersonName(me.displayName)) }
    var phone by remember { mutableStateOf(me.phoneE164.orEmpty()) }
    var address by remember { mutableStateOf(me.barrio) }
    var home by remember { mutableStateOf(if (es) "Depto" else "Apartment") }
    var otherPets by remember { mutableStateOf(if (es) "No" else "No") }
    var species by remember { mutableStateOf(setOf<String>()) }
    var household by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf(if (es) "4 a 8 h" else "4–8 h") }
    var experience by remember { mutableStateOf(if (es) "Ya tuve mascotas" else "I've had pets") }
    var motive by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var sentMatchId by remember { mutableStateOf(existing?.matchId) }
    val homes = if (es) listOf("Depto", "Casa", "Casa con patio") else listOf("Apartment", "House", "House with yard")
    val others = if (es) listOf("No", "Sí, conviven") else listOf("No", "Yes, they live with us")
    val speciesOpts = if (es) {
        listOf("Perro", "Gato", "Ave", "Conejo", "Hámster", "Pez", "Tortuga", "Otro")
    } else {
        listOf("Dog", "Cat", "Bird", "Rabbit", "Hamster", "Fish", "Turtle", "Other")
    }
    val yesPets = others[1]
    val hoursOpts = if (es) listOf("Hasta 4 h", "4 a 8 h", "Más de 8 h") else listOf("Up to 4 h", "4–8 h", "Over 8 h")
    val expOpts = if (es) {
        listOf("Primera mascota", "Ya tuve mascotas", "Tengo experiencia")
    } else {
        listOf("First pet", "I've had pets", "Experienced")
    }
    LaunchedEffect(sent, sentMatchId) {
        val matchId = sentMatchId
        if (!sent || matchId.isNullOrBlank()) return@LaunchedEffect
        delay(900)
        onSent(matchId)
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.petsWantAdopt, onBack = onBack)
        ScreenColumn {
            OgtPill(copy.petsAdoption, OgtColors.sand, OgtColors.ink)
            OgtSectionTitle(listing?.title ?: post.tag)
            OgtCaption(listing?.description ?: post.body)
            if (post.place.isNotBlank()) OgtCaption(post.place)
            if (existing != null || remote.value != null || sent) {
                OgtCard {
                    Text(
                        if (listing?.id?.isUuid() == true && (remote.value != null || sent)) {
                            if (es) "Ya enviaste tu postulación. Quedó en la casilla de quien publicó, junto al formulario."
                            else "You already applied. The poster has the form in their inbox."
                        } else {
                            if (es) "Tu postulación anterior se conserva en este dispositivo, junto al formulario."
                            else "Your previous application and form are preserved on this device."
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = OgtColors.ink,
                    )
                }
                val matchId = sentMatchId ?: remote.value?.matchId ?: existing?.matchId
                if (!matchId.isNullOrBlank()) {
                    OgtPrimaryButton(if (es) "Ver conversación" else "Open conversation") { onSent(matchId) }
                }
                OgtPrimaryButton(if (es) "Volver a la ficha" else "Back to the post", onClick = onBack)
            } else {
            OgtCaption(
                if (listing?.id?.isUuid() == true) {
                    if (es) "Formulario comunitario. Se envía a la casilla de quien publicó. No se publica en el feed."
                    else "Community form. It goes to the poster's inbox, outside the public feed."
                } else {
                    if (es) "Ficha anterior de este dispositivo. El formulario y la conversación se guardan localmente."
                    else "Previous listing on this device. The form and conversation are saved locally."
                },
            )
            AdoptField(if (es) "Tu nombre" else "Your name", if (es) "Nombre y apellido" else "Full name", name) { name = it }
            AdoptField(if (es) "Teléfono" else "Phone", "11 0000 0000", phone, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Phone)) { phone = it }
            AdoptField(
                if (es) "Domicilio o zona del hogar" else "Address or home area",
                if (es) "Podés indicar sólo el barrio o la zona" else "You can provide only your neighborhood or area",
                address,
            ) { address = it }
            if (post.place.isNotBlank()) {
                OgtCaption(
                    if (es) "El aviso está en ${post.place}. El domicilio sirve para ver si la distancia es viable."
                    else "The listing is in ${post.place}. Address helps check if the distance works.",
                )
            }
            AdoptChoice(if (es) "Hogar" else "Home", homes, home) { home = it }
            AdoptChoice(if (es) "¿Hay otras mascotas?" else "Other pets at home?", others, otherPets) { otherPets = it }
            if (otherPets == yesPets) {
                AdoptMultiChoice(
                    label = if (es) "Qué especies conviven" else "Which species live with you",
                    options = speciesOpts,
                    selected = species,
                    onToggle = { option ->
                        species = if (option in species) species - option else species + option
                    },
                )
            }
            Text(
                if (es) "Cómo está formado tu hogar" else "Who lives in your home",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = OgtColors.ink,
            )
            OutlinedTextField(
                value = household,
                onValueChange = { household = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = {
                    Text(
                        if (es) "Cómo es la convivencia. No incluyas nombres ni edades exactas."
                        else "Describe the household without names or exact ages.",
                        color = OgtColors.muted,
                    )
                },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(),
            )
            AdoptChoice(if (es) "Horas fuera de casa" else "Hours away from home", hoursOpts, hours) { hours = it }
            AdoptChoice(if (es) "Experiencia" else "Experience", expOpts, experience) { experience = it }
            Text(if (es) "Por qué este animal" else "Why this animal", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            OutlinedTextField(
                value = motive,
                onValueChange = { motive = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(),
            )
            OgtCaption(
                if (es) "Adopción responsable: no es un regalo, incluye veterinario y un hogar para toda la vida."
                else "Responsible adoption: not a gift. Includes a vet and a lifelong home.",
            )
            sendError?.let { Text(it,color=OgtColors.error) }
            OgtPrimaryButton(if(sending) "Enviando…" else if (es) "Enviar postulación" else "Send application", enabled=!sending) {

                val needsSpecies = otherPets == yesPets && species.isEmpty()
                if (name.isBlank() || phone.isBlank() || address.isBlank() || household.isBlank() || motive.isBlank() || needsSpecies) {
                    sendError = if(es) "Completá los datos del formulario antes de enviarlo." else "Complete the application before sending it."
                    return@OgtPrimaryButton
                }
                if (listing?.id?.isUuid() == true) {
                    sending=true;sendError=null
                    val owner=me.id
                    val form=com.onlygoodthings.shared.domain.AdoptionApplicationForm(titleCasePersonName(name),phone.trim(),address.trim(),home,otherPets,if(otherPets==yesPets)species.joinToString(", ") else "",household.trim(),hours,experience,motive.trim())
                    scope.launch {
                        try {
                            check(session.me().id==owner)
                            val thread=auth.animals.contact(com.onlygoodthings.shared.domain.AnimalContactRequest(listing.id,"ADOPTION",form))
                            check(session.me().id==owner)
                            sentMatchId=thread.matchId;sent=true
                        } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                        catch(_:Exception) { sendError=if(es) "No se pudo confirmar el envío. Tu formulario se conserva para reintentarlo." else "Could not confirm delivery. Your form is preserved for retry." }
                        finally { sending=false }
                    }
                    return@OgtPrimaryButton
                }
                val ok = db.addAdoptRequest(
                    viewer = me,
                    postId = postId,
                    listingId = listing?.id,
                    applicantName = titleCasePersonName(name),
                    phone = phone,
                    address = address,
                    homeKind = home,
                    otherPets = otherPets,
                    otherSpecies = if (otherPets == yesPets) species.joinToString(", ") else "",
                    household = household,
                    hoursAway = hours,
                    experience = experience,
                    motive = motive,
                )
                if (ok != null) {
                    sentMatchId = ok.matchId
                    sent = true
                }
            }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdoptField(label: String, hint: String, value: String, keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization=androidx.compose.ui.text.input.KeyboardCapitalization.Sentences), onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = OgtColors.muted) },
            singleLine = true,
            shape = RoundedCornerShape(OgtDimens.buttonRadius),
            colors = ogtOutlinedFieldColors(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdoptChoice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val on = option == selected
                Column(Modifier.clickable { onSelect(option) }) {
                    OgtPill(
                        option,
                        if (on) OgtColors.secondary else OgtColors.sand,
                        if (on) OgtColors.onPrimary else OgtColors.ink,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdoptMultiChoice(
    label: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val on = option in selected
                Column(Modifier.clickable { onToggle(option) }) {
                    OgtPill(
                        option,
                        if (on) OgtColors.secondary else OgtColors.sand,
                        if (on) OgtColors.onPrimary else OgtColors.ink,
                    )
                }
            }
        }
    }
}
