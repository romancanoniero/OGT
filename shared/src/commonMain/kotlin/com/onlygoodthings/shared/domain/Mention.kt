package com.onlygoodthings.shared.domain

/**
 * Referencia estilo Instagram: @nick en la UI, handle sin arroba en datos.
 * [userId] null y [honorId] set = invitación pendiente.
 * Ambos null = solo el nombre (homenaje a quien ya no está o no usa la app).
 */
data class MentionCandidate(
    val userId: String?,
    val displayName: String,
    val handle: String,
    val honorId: String? = null,
) {
    val atHandle: String get() = "@$handle"
    val pending: Boolean get() = !honorId.isNullOrBlank() && userId.isNullOrBlank()
    val namedOnly: Boolean get() = userId.isNullOrBlank() && honorId.isNullOrBlank()
}

/** Nombre escrito, sin usuario ni invitación. */
fun namedMention(displayName: String): MentionCandidate {
    val name = titleCasePersonName(displayName.trim())
    return MentionCandidate(
        userId = null,
        displayName = name,
        handle = mentionHandle(name),
    )
}

/** Slug tipo Instagram a partir del nombre visible. */
fun mentionHandle(displayName: String): String {
    val slug = buildString {
        var dot = false
        foldMention(displayName).forEach { ch ->
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> {
                    append(ch)
                    dot = false
                }
                ch == '.' || ch == ' ' || ch == '-' || ch == '_' -> {
                    if (!dot && isNotEmpty()) {
                        append('.')
                        dot = true
                    }
                }
            }
        }
    }.trim('.')
    return slug.ifBlank { "vecina" }
}

/**
 * Typeahead Instagram: @ se ignora; gana el nick que empieza igual,
 * después el nombre, después un contiene.
 */
fun searchMentions(
    query: String,
    pool: List<MentionCandidate>,
    limit: Int = 8,
): List<MentionCandidate> {
    val q = foldMention(query.trim().removePrefix("@"))
    if (q.isEmpty()) return pool.take(limit)
    return pool.mapNotNull { hit ->
        val handle = foldMention(hit.handle)
        val name = foldMention(hit.displayName)
        val score = when {
            handle == q -> 0
            handle.startsWith(q) -> 1
            name.startsWith(q) -> 2
            handle.contains(q) -> 3
            name.split(' ').any { it.startsWith(q) } -> 4
            name.contains(q) -> 5
            else -> return@mapNotNull null
        }
        score to hit
    }.sortedWith(compareBy({ it.first }, { it.second.handle }))
        .map { it.second }
        .take(limit)
}

fun foldMention(raw: String): String = buildString {
    raw.lowercase().forEach { ch ->
        append(
            when (ch) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                else -> ch
            },
        )
    }
}
