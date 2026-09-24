package com.onlygoodthings.shared.domain

/**
 * Motivos de reporte. Códigos estables para Postgres; el texto es para la hoja.
 * Instagram / TikTok / Meta aportan estafa, odio, sexo y spam;
 * acá se recortan a lo que puede romper un feed de buenas acciones.
 */
data class ReportMotive(
    val code: String,
    val label: String,
    val hint: String? = null,
    val children: List<ReportMotive> = emptyList(),
)

object PostReportCatalog {
    val OffTopic = ReportMotive(
        code = "OFF_TOPIC",
        label = "No es una buena acción",
        hint = "Queja, pelea, proselitismo o autopromoción.",
        children = listOf(
            ReportMotive("OFF_TOPIC_PROMO", "Es publicidad o autopromoción"),
            ReportMotive("OFF_TOPIC_PROSELYTISM", "Es proselitismo: fe, partido o causa"),
            ReportMotive("OFF_TOPIC_FIGHT", "Es queja, pelea o política"),
            ReportMotive("OFF_TOPIC_MOCK", "Es burla o ironía"),
            ReportMotive("OFF_TOPIC_NONE", "No ayuda a nadie de la comunidad"),
        ),
    )
    val FalseInfo = ReportMotive(
        code = "FALSE",
        label = "Información falsa o engañosa",
        hint = "Una alerta o noticia que no es cierta.",
        children = listOf(
            ReportMotive("FALSE_LOST_PET", "Alerta de mascota falsa o ya resuelta"),
            ReportMotive("FALSE_PARKING", "Lugar de estacionamiento inventado"),
            ReportMotive("FALSE_NEWS", "Noticia o dato falso"),
            ReportMotive("FALSE_IMPERSONATION", "Se hace pasar por otra persona"),
        ),
    )
    val Scam = ReportMotive(
        code = "SCAM",
        label = "Estafa o pedido de dinero",
        hint = "Cobros, enlaces o premios sospechosos.",
        children = listOf(
            ReportMotive("SCAM_MONEY", "Pide dinero, datos o transferencias"),
            ReportMotive("SCAM_LINK", "Enlace o premio sospechoso"),
            ReportMotive("SCAM_ADOPTION", "Adopción o rescate con cobro"),
        ),
    )
    val Harm = ReportMotive(
        code = "HARM",
        label = "Daño a una persona o animal",
        hint = "Acoso, contenido sexual o maltrato.",
        children = listOf(
            ReportMotive("HARM_HARASSMENT", "Acoso, insultos o exposición"),
            ReportMotive("HARM_SEXUAL", "Contenido sexual"),
            ReportMotive("HARM_ANIMAL", "Maltrato o explotación animal"),
            ReportMotive("HARM_DANGER", "Incitá a algo peligroso"),
        ),
    )
    val Honor = ReportMotive(
        code = "HONOR",
        label = "No debería estar en un homenaje",
        hint = "Falta de respeto o historia inventada.",
        children = listOf(
            ReportMotive("HONOR_DISRESPECT", "Falta el respeto a quien se honra"),
            ReportMotive("HONOR_FAKE", "Historia o foto inventada"),
            ReportMotive("HONOR_CONSENT", "Nombre o foto sin consentimiento"),
        ),
    )
    val Other = ReportMotive(
        code = "OTHER",
        label = "Otro motivo",
        hint = "Contanos en una línea, si querés.",
    )

    fun motives(kind: FeedCardKind? = null): List<ReportMotive> = buildList {
        add(OffTopic)
        add(FalseInfo)
        add(Scam)
        add(Harm)
        if (kind == null || kind == FeedCardKind.HOMENAJE) add(Honor)
        add(Other)
    }

    fun byCode(code: String): ReportMotive? =
        motives().asSequence().flatMap { listOf(it) + it.children }.firstOrNull { it.code == code }
}
