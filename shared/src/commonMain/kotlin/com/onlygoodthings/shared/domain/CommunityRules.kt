package com.onlygoodthings.shared.domain

/**
 * Reglas de la comunidad. Hechos que ayudan, no púlpitos.
 * El proselitismo (fe, partido o causa) no entra: acá se suma, no se convence.
 */
data class CommunityRuleSection(
    val title: String,
    val lead: String,
    val points: List<String>,
)

object CommunityRules {
    const val title = "Reglas de la comunidad"
    const val kicker = "Cómo se publica acá"

    const val preamble =
        "Only Good Things nació para que se vea lo que alguien hizo por otra persona: " +
            "ceder un lugar, encontrar un animal, dar una mano, honrar a alguien, contar que algo salió bien. " +
            "No es un púlpito ni una campaña. Quien entra, entra a sumar. No a convertir a nadie."

    val sections = listOf(
        CommunityRuleSection(
            title = "Se cuenta lo que se hizo, no lo que hay que creer",
            lead = "Hechos e invitaciones concretas. No doctrinas.",
            points = listOf(
                "Publicá una acción, un aviso útil o una convocatoria con día y lugar.",
                "No uses la app para predicar, reclutar ni pedir adhesión a una fe, un partido o una ideología.",
                "Un homenaje recuerda a una persona. No es culto, ni mitin, ni llamado a seguir una causa.",
                "Podés tener creencias. No las uses acá para convencer a quien lee.",
            ),
        ),
        CommunityRuleSection(
            title = "El aplauso no prueba el bien",
            lead = "La reputación sale de lo que se logró, no de lo que se pagó ni de cuántos aplauden.",
            points = listOf(
                "“Se logró” vale más que un clap.",
                "La plata puede dar alcance. No compra puntos ni confianza.",
                "No inventes impacto. Si no pasó, no se publica como si hubiera pasado.",
            ),
        ),
        CommunityRuleSection(
            title = "La ayuda de cerca tiene que ser cierta",
            lead = "Parking, mascotas y convocatorias viven de la confianza.",
            points = listOf(
                "Un lugar vacío, una mascota perdida o una merienda tienen que existir.",
                "Mentir un alerta o un lugar rompe el mapa para todos.",
                "Si ya se resolvió, cerrá el aviso. No lo dejes vivo para sumar visitas.",
            ),
        ),
        CommunityRuleSection(
            title = "Las personas y los animales no son contenido",
            lead = "Nadie se usa de gancho. Nadie se cobra por ser adoptado.",
            points = listOf(
                "Sin acoso, insultos, exposición ni contenido sexual.",
                "Sin maltrato ni explotación animal.",
                "Adopción y rescate sin cobro. Fotos y nombres con consentimiento.",
                "No pidas dinero, datos bancarios ni “donaciones urgentes” en un post.",
            ),
        ),
        CommunityRuleSection(
            title = "El homenaje es de quien se honra",
            lead = "Se recuerda una vida. No se predica sobre ella.",
            points = listOf(
                "Contá una historia real, con respeto.",
                "Nombre y foto: con permiso de la persona o de su familia.",
                "No conviertas el homenaje en un discurso, una campaña o una recluta.",
            ),
        ),
        CommunityRuleSection(
            title = "Una mano por otra, no un milagro",
            lead = "El intercambio de ayuda es un saber o un rato. No una captación.",
            points = listOf(
                "Ofrecé lo que sabés hacer: un arreglo, una clase, un trámite.",
                "El día y la hora se hablan en privado.",
                "No vendas curas, promesas ni pertenencia a un grupo.",
            ),
        ),
        CommunityRuleSection(
            title = "Si no suma, no va",
            lead = "Hay otros lugares de internet para la pelea y la marca.",
            points = listOf(
                "Queja, ironía, política de trinchera o autopromoción no son una buena acción.",
                "Una noticia entra si informa un hecho que ayuda o alegra. No si busca afiliar.",
            ),
        ),
        CommunityRuleSection(
            title = "Si se rompe, reportá",
            lead = "Ocultar es para vos. Reportar es para todos.",
            points = listOf(
                "Elegí el motivo. Moderación lee el código, no un juicio.",
                "Reportar en falso también rompe la confianza.",
            ),
        ),
    )
}
