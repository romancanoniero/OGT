package com.onlygoodthings.shared.domain

/** Rules for new recognitions. Historical posts and other publication flows keep their contracts. */
object GoodActionRecognition {
    private val topics = setOf(
        "Reforestación", "Limpieza", "Reciclaje", "Ayuda comunitaria", "Ayuda vecinal",
        "Rescate Animal", "Movilidad Eco",
    ).map { foldMention(it) }.toSet()

    fun isAction(topic: String, publicationKind: String? = null): Boolean = when (publicationKind) {
        null -> foldMention(topic.trim()) in topics // Compatibility with existing mobile clients.
        "ACTION" -> true
        "CONVOCATORIA", "TERNURA", "HOMENAJE", "POST" -> false
        else -> throw IllegalArgumentException("Tipo de publicación inválido")
    }

    fun validate(authorId: String, protagonistId: String?, honoreeName: String?, authorName: String? = null) {
        val hero = protagonistId?.trim()?.takeIf { it.isNotEmpty() }
        require(hero == null || !hero.equals(authorId, ignoreCase = true)) {
            "Dar a conocer es para reconocer una buena acción de otra persona."
        }
        require(hero != null || !honoreeName.isNullOrBlank()) {
            "Elegí a la persona que hizo esta buena acción."
        }
        // An explicit account distinguishes people who happen to share the same name.
        if (hero == null && !authorName.isNullOrBlank()) {
            require(mentionHandle(honoreeName.orEmpty().removePrefix("@")) != mentionHandle(authorName)) {
                "Elegí a otra persona; si tiene tu mismo nombre, seleccioná su cuenta o escribí su nombre completo."
            }
        }
    }
}
