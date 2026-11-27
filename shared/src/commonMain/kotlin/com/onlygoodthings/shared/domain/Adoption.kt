package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Stable CRM identity and meaning; labels may change without rewriting existing listings. */
@Serializable
data class AdoptionTag(
    val id: String,
    val label: String = "",
    val key: String = "",
    val group: String = "OTHER",
    val legacyValue: String = "",
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val version: Int = 1,
)

/** Null on historical listings: their original fields remain authoritative and untouched. */
@Serializable
data class AdoptionDetails(
    val version: Int = 1,
    val presentation: String = "",
    val historyAndCare: String = "",
    val tags: List<AdoptionTag> = emptyList(),
    val freeHashtags: List<String> = emptyList(),
    val adoptionMode: String = "UNSPECIFIED",
    val availableCount: Int? = null,
)

object AdoptionRules {
    const val MAX_TITLE = 120
    const val MAX_PRESENTATION = 1500
    const val MAX_HISTORY = 2000
    const val MAX_TAGS = 24
    val groups = listOf("SPECIES", "SIZE", "SEX", "VACCINATION", "STERILIZATION", "OTHER")
    val exclusiveGroups = setOf("VACCINATION", "STERILIZATION")
    val modes = listOf("UNSPECIFIED", "SEPARATE", "TOGETHER")
    fun groupLabel(group: String): String = when (group) {
        "SPECIES" -> "Especie"; "SIZE" -> "Tamaño"; "SEX" -> "Sexo"
        "VACCINATION" -> "Vacunación"; "STERILIZATION" -> "Castración"; else -> "Otras etiquetas"
    }
    fun modeLabel(mode: String): String = when (mode) {
        "SEPARATE" -> "Se pueden adoptar por separado"
        "TOGETHER" -> "Buscan un hogar juntos"
        else -> ""
    }
    fun toggle(selected: List<AdoptionTag>, tag: AdoptionTag): List<AdoptionTag> =
        if (selected.any { it.id == tag.id }) selected.filterNot { it.id == tag.id }
        else selected.filterNot { tag.group in exclusiveGroups && it.group == tag.group } + tag

    fun legacyTags(catalog: List<AdoptionTag>, species: String, size: String?, sex: String, vaccinated: Boolean, sterilized: Boolean): List<AdoptionTag> =
        catalog.filter { tag -> when (tag.group) {
            "SPECIES" -> tag.legacyValue == species
            "SIZE" -> !size.isNullOrBlank() && tag.legacyValue == size
            "SEX" -> sex.isNotBlank() && tag.legacyValue == sex
            "VACCINATION" -> vaccinated && tag.legacyValue == "ALL"
            "STERILIZATION" -> sterilized && tag.legacyValue == "ALL"
            else -> false
        } }

    /** Resolve IDs through the server catalog. Never trust client labels or health meanings. */
    fun normalize(draft: AdoptionDetails, catalog: List<AdoptionTag>, previousIds: Set<String> = emptySet()): AdoptionDetails {
        require(draft.version == 1) { "Versión de ficha de adopción no compatible." }
        require(draft.presentation.length <= MAX_PRESENTATION) { "La presentación admite hasta $MAX_PRESENTATION caracteres." }
        require(draft.historyAndCare.length <= MAX_HISTORY) { "Historia y cuidados admite hasta $MAX_HISTORY caracteres." }
        require(draft.adoptionMode in modes) { "Elegí cómo se pueden adoptar." }
        require(draft.availableCount == null || draft.availableCount in 1..100) { "La cantidad disponible debe estar entre 1 y 100." }
        require(draft.tags.size <= MAX_TAGS && draft.freeHashtags.size <= 6) { "Hay demasiadas etiquetas." }
        val byId = catalog.associateBy { it.id }
        val selected = draft.tags.map { requested ->
            val tag = requireNotNull(byId[requested.id]) { "Una etiqueta ya no está disponible. Actualizá el catálogo." }
            require(tag.active || tag.id in previousIds) { "La etiqueta ${tag.label} ya no está disponible para nuevos usos." }
            tag
        }.distinctBy { it.id }.toMutableList()
        val free = mutableListOf<String>()
        for (raw in draft.freeHashtags) {
            val hashtag = Hashtags.free(raw)
            val official = catalog.firstOrNull { it.key == hashtag.key || Hashtags.key(it.label) == hashtag.key }
            if (official != null) {
                require(official.active || official.id in previousIds) { "Esa etiqueta fue desactivada en el CRM." }
                if (selected.none { it.id == official.id }) selected += official
            } else if (free.none { Hashtags.key(it) == hashtag.key }) free += hashtag.label
        }
        require(selected.size + free.size <= MAX_TAGS) { "Podés elegir hasta $MAX_TAGS etiquetas." }
        require(selected.any { it.group == "SPECIES" }) { "Elegí al menos una especie." }
        for (group in exclusiveGroups) require(selected.count { it.group == group } <= 1) { "Elegí una sola opción de ${groupLabel(group).lowercase()}." }
        return draft.copy(presentation = draft.presentation.trim(), historyAndCare = draft.historyAndCare.trim(), tags = selected, freeHashtags = free)
    }
    fun hashtags(details: AdoptionDetails): List<PostHashtag> =
        (listOf(PostHashtag("adopcion", "Adopción")) + details.tags.map { PostHashtag(it.key, it.label) } + details.freeHashtags.map(Hashtags::free)).distinctBy { it.key }
    fun singleValue(details: AdoptionDetails, group: String): String = details.tags.filter { it.group == group }.singleOrNull()?.legacyValue.orEmpty()
}
