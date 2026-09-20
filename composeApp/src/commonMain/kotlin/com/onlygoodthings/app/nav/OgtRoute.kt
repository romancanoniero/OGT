package com.onlygoodthings.app.nav

enum class OgtRoute(val title: String) {
    Splash("Splash"),
    AnimatedSvg("Logo animado"),
    SplashSponsors("Splash con sponsors"),
    OnboardingFeatures("Onboarding: 4 funcionalidades"),
    Onboarding("Onboarding"),
    SignUp("Crear cuenta"),
    Login("Iniciar sesión"),
    Otp("Verificación OTP"),
    Recover("Recuperar contraseña"),
    Permissions("Permisos"),
    Gps("Permiso GPS"),
    Identity("Verificación"),
    Feed("Feed"),
    PostDetail("Detalle de publicación"),
    Publish("Dar a conocer"),
    Parking("Estacionamiento"),
    ParkingConfirm("Cesión confirmada"),
    ParkHere("Estacioné aquí"),
    Map("Mapa de estacionamiento"),
    PetsMap("Mapa de mascotas"),
    Animals("Mascotas"),
    ReportAnimal("Reportar animal"),
    AdoptApply("Quiero adoptar"),
    Skills("Intercambio de Ayuda"),
    ProposeSkill("Publicar intercambio"),
    Messages("Mensajes"),
    Chat("Chat de trueque"),
    Wallet("Billetera de puntos"),
    Ranking("Ranking de la comunidad"),
    Notifications("Notificaciones"),
    Invite("Invitar amigos"),
    ClaimHonor("Reivindicar mención"),
    Settings("Configuración"),
    NeighborProfile("Perfil"),
    Sponsors("Beneficios RSE"),
    Milestone("Hito comunitario"),
}

val OgtBottomTabs = listOf(
    OgtRoute.Feed,
    OgtRoute.Parking,
    OgtRoute.Skills,
    OgtRoute.Settings,
)

/** Tipos de aviso que se publican desde el + del dock. */
enum class ComposePostKind {
    ACTION,
    GATHERING,
    LOST,
    ADOPT,
    SKILL,
    TERNURA,
    HOMENAJE,
}
