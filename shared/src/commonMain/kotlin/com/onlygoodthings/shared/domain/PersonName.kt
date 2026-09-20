package com.onlygoodthings.shared.domain

/**
 * Capitaliza nombre y apellido como en un documento.
 * La primera letra de cada palabra, salvo preposiciones y partículas
 * (de, del, la, y, von…) que no sean la primera palabra.
 */
fun titleCasePersonName(raw: String): String {
    if (raw.isEmpty()) return raw
    val trailing = raw.takeLastWhile { it == ' ' || it == '\t' }
    val core = raw.dropLast(trailing.length)
    if (core.isBlank()) return raw
    val parts = core.split(' ')
    val titled = parts.mapIndexed { index, word ->
        if (word.isEmpty()) word
        else titleCaseWord(word, particle = index > 0 && isNameParticle(word))
    }
    return titled.joinToString(" ") + trailing
}

private fun titleCaseWord(word: String, particle: Boolean): String {
    if (particle) return word.lowercase()
    return buildString {
        var cap = true
        word.forEach { ch ->
            when {
                ch == '-' || ch == '\'' -> {
                    append(ch)
                    cap = true
                }
                cap && ch.isLetter() -> {
                    append(ch.uppercaseChar())
                    cap = false
                }
                else -> {
                    append(if (ch.isLetter()) ch.lowercaseChar() else ch)
                    cap = false
                }
            }
        }
    }
}

private fun isNameParticle(word: String): Boolean =
    word.lowercase() in NAME_PARTICLES

private val NAME_PARTICLES = setOf(
    "de", "del", "la", "las", "los", "el", "y", "e", "o", "u", "a",
    "da", "das", "do", "dos", "van", "von", "di", "du",
    "of", "the", "and", "und",
)
