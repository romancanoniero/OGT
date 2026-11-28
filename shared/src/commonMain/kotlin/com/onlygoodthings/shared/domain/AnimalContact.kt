package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class AdoptionApplicationForm(
    val applicantName: String,
    val phone: String,
    val address: String,
    val homeKind: String,
    val otherPets: String,
    val otherSpecies: String = "",
    val household: String,
    val hoursAway: String,
    val experience: String,
    val motive: String,
)

@Serializable
data class AnimalContactRequest(
    val listingId: String,
    val kind: String,
    val form: AdoptionApplicationForm? = null,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val observedAtEpochMs: Long? = null,
)

@Serializable
data class AnimalSightingDto(
    val id: String,
    val listingId: String,
    val postId: String,
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val matchId: String? = null,
    val createdAtEpochMs: Long,
    val observedAtEpochMs: Long? = null,
)

fun validateSightingObservedAt(observedAtEpochMs: Long, nowEpochMs: Long? = null) {
    require(observedAtEpochMs > 0) { "Indicá una fecha y hora válidas para el avistamiento" }
    require(nowEpochMs == null || observedAtEpochMs <= nowEpochMs + 300_000L) {
        "La fecha del avistamiento no puede ser futura"
    }
}

fun validateAnimalContact(request: AnimalContactRequest, nowEpochMs: Long? = null) {
    require(request.kind in setOf("ADOPTION", "SIGHTING")) { "Tipo de contacto inválido" }
    require(request.note.length <= 800) { "El mensaje admite hasta 800 caracteres" }
    if (request.kind == "ADOPTION") {
        val form = requireNotNull(request.form) { "Completá el formulario" }
        val fields = listOf(form.applicantName, form.phone, form.address, form.homeKind,
            form.otherPets, form.household, form.hoursAway, form.experience, form.motive)
        require(fields.all { it.isNotBlank() && it.length <= 1000 } && form.otherSpecies.length <= 200) { "Revisá los datos del formulario" }
        require(request.latitude == null && request.longitude == null) { "El formulario no requiere ubicación precisa" }
        require(request.observedAtEpochMs == null) { "El formulario de adopción no requiere horario de avistamiento" }
    } else {
        require(request.form == null) { "El avistaje no admite un formulario de adopción" }
        request.observedAtEpochMs?.let { validateSightingObservedAt(it, nowEpochMs) }
        require(request.latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
            request.longitude?.let { it.isFinite() && it in -180.0..180.0 } == true) { "Indicá dónde viste a la mascota" }
    }
}
