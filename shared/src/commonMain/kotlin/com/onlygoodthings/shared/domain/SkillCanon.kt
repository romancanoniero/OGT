package com.onlygoodthings.shared.domain

/**
 * Oficio canónico: se guarda el oficio, no la persona.
 * "albañil" y "Albañilería" son la misma categoría.
 */
object SkillCanon {

    data class Trade(val label: String, val aliases: Set<String>)

    val trades: List<Trade> = listOf(
        Trade("Albañilería", setOf("albanil", "albanileria", "obra", "construccion", "constructor")),
        Trade("Carpintería", setOf("carpintero", "carpintera", "carpinteria", "madera")),
        Trade("Plomería", setOf("plomero", "plomera", "plomeria", "canos", "caneria")),
        Trade("Electricidad", setOf("electricista", "electricidad", "electrico")),
        Trade("Gas", setOf("gasista", "gas")),
        Trade("Pintura", setOf("pintor", "pintora", "pintura")),
        Trade("Jardinería", setOf("jardinero", "jardinera", "jardineria", "plantas", "huerta")),
        Trade("Costura", setOf("costurero", "costurera", "costura", "arreglo de ropa", "modista")),
        Trade("Herrería", setOf("herrero", "herrera", "herreria")),
        Trade("Cerrajería", setOf("cerrajero", "cerrajera", "cerrajeria")),
        Trade("Mecánica", setOf("mecanico", "mecanica", "auto", "autos")),
        Trade("Peluquería", setOf("peluquero", "peluquera", "peluqueria", "corte de pelo")),
        Trade("Cocina solidaria", setOf("cocina", "cocinero", "cocinera", "cocinar", "comida")),
        Trade("Tutorías", setOf("tutoria", "tutorias", "clases", "profesor", "profesora", "maestro")),
        Trade("Traducción", setOf("traductor", "traductora", "traduccion", "ingles", "idiomas")),
        Trade("Ayuda tecnológica", setOf("computadora", "computacion", "pc", "wifi", "tecnologia", "celular")),
        Trade("Cuidado de mascotas", setOf("mascotas", "pasear perros", "perro", "gato", "veterinaria")),
        Trade("Acompañamiento", setOf("acompanamiento", "acompanante", "compania")),
        Trade("Reparaciones menores", setOf("reparaciones", "arreglos", "mantenimiento", "manitas")),
        Trade("Limpieza", setOf("limpiar", "limpieza", "empleada domestica")),
        Trade("Mudanza", setOf("mudanza", "flete", "cargar")),
        Trade("Cuidado de niños", setOf("nina", "ninos", "niñera", "ninera", "babysitter")),
        Trade("Cuidado de mayores", setOf("mayores", "abuelos", "cuidador")),
        Trade("Clases de música", setOf("musica", "guitarra", "piano", "canto")),
        Trade("Diseño gráfico", setOf("diseno", "disenador", "grafica")),
        Trade("Fotografía", setOf("fotografo", "fotografia", "fotos")),
    )

    fun foldKey(raw: String): String {
        val lower = raw.trim().lowercase()
        val mapped = buildString {
            for (ch in lower) {
                when (ch) {
                    'á', 'à', 'ä', 'â' -> append('a')
                    'é', 'è', 'ë', 'ê' -> append('e')
                    'í', 'ì', 'ï', 'î' -> append('i')
                    'ó', 'ò', 'ö', 'ô' -> append('o')
                    'ú', 'ù', 'ü', 'û' -> append('u')
                    'ñ' -> append('n')
                    'ç' -> append('c')
                    else -> if (ch.isLetterOrDigit()) append(ch)
                    else if (ch.isWhitespace()) append(' ')
                }
            }
        }
        return mapped.replace(Regex("\\s+"), " ").trim()
    }

    fun prettyLabel(raw: String): String {
        val small = setOf("de", "del", "la", "el", "los", "las", "y", "o")
        val words = raw.trim().replace(Regex("\\s+"), " ").split(' ')
        return words.filter { it.isNotBlank() }.mapIndexed { index, word ->
            val folded = foldKey(word)
            if (index > 0 && folded in small) word.lowercase()
            else word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }.joinToString(" ")
    }

    /** Etiqueta canónica para guardar o sugerir. */
    fun canonicalLabel(raw: String): String {
        val key = foldKey(raw)
        if (key.length < 2) return prettyLabel(raw)
        tradeFor(key)?.let { return it.label }
        personToTrade(key)?.let { return it }
        return prettyLabel(raw)
    }

    fun sameTrade(a: String, b: String): Boolean = foldKey(canonicalLabel(a)) == foldKey(canonicalLabel(b))

    fun matchesQuery(query: String, label: String): Boolean {
        val q = foldKey(query)
        if (q.isEmpty()) return true
        val foldedLabel = foldKey(label)
        val canonQ = foldKey(canonicalLabel(query))
        val canonL = foldKey(canonicalLabel(label))
        if (q.length == 1) return foldedLabel.startsWith(q) || canonL.startsWith(q)
        return foldedLabel.contains(q) ||
            canonL.contains(q) ||
            canonL == canonQ ||
            q.contains(foldedLabel) && foldedLabel.length >= 4 ||
            tradeFor(q)?.let { foldKey(it.label) == canonL } == true
    }

    private fun tradeFor(folded: String): Trade? {
        if (folded.isEmpty()) return null
        return trades.firstOrNull { trade ->
            foldKey(trade.label) == folded || folded in trade.aliases ||
                trade.aliases.any { alias -> alias == folded || (folded.length >= 3 && alias.startsWith(folded)) }
        }
    }

    /** carpintero → Carpintería. No inventa oficios sin esa forma. */
    private fun personToTrade(folded: String): String? {
        val base = folded.removeSuffix("s")
        val stem = when {
            base.endsWith("ero") && base.length > 5 -> base.dropLast(3)
            base.endsWith("era") && base.length > 5 -> base.dropLast(3)
            else -> return null
        }
        val trade = stem + "ería"
        return prettyLabel(trade)
    }
}
