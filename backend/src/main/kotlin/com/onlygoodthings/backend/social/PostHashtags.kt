package com.onlygoodthings.backend.social

import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.backend.http.JsonBody
import java.sql.Connection

internal data class ResolvedHashtags(val tags: List<PostHashtag>, val recognitionCategoryId: String?)

internal object PostHashtags {
    fun read(raw: String?): List<PostHashtag> = if (raw.isNullOrBlank()) emptyList() else
        JsonBody.objectMapper.readValue(raw, object : com.fasterxml.jackson.core.type.TypeReference<List<PostHashtag>>() {})

    fun json(tags: List<PostHashtag>): String = JsonBody.objectMapper.writeValueAsString(tags.map {
        mapOf("key" to it.key,"label" to it.label,"categoryId" to it.categoryId)
    })

    fun resolve(c: Connection, raw: List<String>, previous: List<PostHashtag> = emptyList()): ResolvedHashtags {
        require(raw.isNotEmpty() && raw.size <= Hashtags.MAX_TAGS) { "Elegí entre 1 y ${Hashtags.MAX_TAGS} hashtags." }
        var recognition: String? = null
        val tags = raw.map { text ->
            val key = Hashtags.key(text)
            val category = c.prepareStatement("SELECT id,label,tag_key,active,requires_recognition FROM recognition_categories WHERE tag_key=? OR translate(lower(regexp_replace(label,'[^[:alnum:]_]','','g')),'áéíóúüñ','aeiouun')=? LIMIT 1 FOR SHARE").use { q ->
                q.setString(1,key);q.setString(2,key)
                q.executeQuery().use { r -> if(r.next()) {
                    val id=r.getString("id")
                    require(r.getBoolean("active") || previous.any { it.categoryId==id }) { "Este hashtag ya no está disponible. Elegí otro." }
                    if(r.getBoolean("requires_recognition"))recognition=recognition ?: id
                    PostHashtag(r.getString("tag_key") ?: key,r.getString("label"),id)
                } else null }
            }
            category ?: Hashtags.free(text)
        }.distinctBy { it.interestKey }
        return ResolvedHashtags(tags,recognition)
    }
}
