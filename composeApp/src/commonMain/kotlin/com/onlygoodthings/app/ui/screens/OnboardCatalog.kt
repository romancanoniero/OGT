package com.onlygoodthings.app.ui.screens

import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.onboard_feed
import com.onlygoodthings.app.resources.onboard_parking
import com.onlygoodthings.app.resources.onboard_pets
import com.onlygoodthings.app.resources.onboard_puntos
import com.onlygoodthings.app.resources.onboard_skills
import org.jetbrains.compose.resources.DrawableResource

/** Escenas de producto: onboarding y reel de login. Un solo copy. */
internal data class OnboardPage(
    val image: DrawableResource,
    val kicker: String,
    val title: String,
    val body: String,
)

internal val OnboardPages = listOf(
    OnboardPage(
        image = Res.drawable.onboard_parking,
        kicker = "MOVILIDAD SOSTENIBLE",
        title = "Parking colaborativo en tiempo real",
        body = "Avisá cuando dejás tu lugar o encontrá el de otra persona en segundos. Ahorrás combustible y sumás puntos de comunidad.",
    ),
    OnboardPage(
        image = Res.drawable.onboard_skills,
        kicker = "INTERCAMBIO DE AYUDA",
        title = "Una mano por otra",
        body = "Pedí una tarea o un saber y ofrecé lo tuyo: una clase, un arreglo, un trámite. El día y la hora se hablan en privado.",
    ),
    OnboardPage(
        image = Res.drawable.onboard_pets,
        kicker = "BIENESTAR ANIMAL",
        title = "Alertas de comunidad y adopciones",
        body = "Reportá mascotas perdidas con alerta en 2 km. Coordiná adopciones y hogares temporales con tu comunidad, cerca o en la red.",
    ),
    OnboardPage(
        image = Res.drawable.onboard_feed,
        kicker = "COMUNIDAD ACTIVA",
        title = "Feed de buenas acciones",
        body = "Sumá karma ecológico por cada acto solidario y canjealo en comercios de cercanía de tu comunidad.",
    ),
    OnboardPage(
        image = Res.drawable.onboard_puntos,
        kicker = "RECONOCIMIENTO COMUNITARIO",
        title = "Ganá puntos y apoyá a quienes hacen el bien",
        body = "Cada colaboración suma puntos de comunidad y medallas de gratitud. El ranking reconoce a quienes ayudan, cerca o en la red.",
    ),
)
