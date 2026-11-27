package com.onlygoodthings.app.nav

enum class OgtRoute(val title: String) {
    Splash("Splash"),
    Landing("Presentación"),
    AnimatedSvg("Logo animado"),
    SplashSponsors("Splash con sponsors"),
    OnboardingFeatures("Onboarding: 4 funcionalidades"),
    Onboarding("Onboarding"),
    SignUp("Crear cuenta"),
    Login("Iniciar sesión"),
    UnlockSession("Desbloquear OGT"),
    Otp("Verificación OTP"),
    Recover("Recuperar contraseña"),
    CommunityAgreement("Bienvenido a Only Good Things"),
    CompleteProfile("Completá tu perfil"),
    Permissions("Permisos"),
    Gps("Permiso GPS"),
    Identity("Verificación"),
    Feed("Feed"),
    PostDetail("Detalle de publicación"),
    Publish("Publicar"),
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
    PostInvitation("Invitación a una publicación"),
    ClaimHonor("Reivindicar mención"),
    Settings("Configuración"),
    Vehicle("Auto"),
    Language("Idioma"),
    CommunityRules("Reglas de la comunidad"),
    NeighborProfile("Perfil"),
    Sponsors("Beneficios RSE"),
    Milestone("Hito comunitario"),
}

val OgtBottomTabs = listOf(
    OgtRoute.Feed,
    OgtRoute.Parking,
    OgtRoute.Skills,
)

internal fun authenticatedStartRoute(needsConsent: Boolean, needsProfile: Boolean, onboardingDone: Boolean): OgtRoute = when {
    needsConsent -> OgtRoute.CommunityAgreement
    needsProfile -> OgtRoute.CompleteProfile
    !onboardingDone -> OgtRoute.Permissions
    else -> OgtRoute.Feed
}

/** A saved session protected by biometrics needs unlocking, not another provider sign-in. */
internal fun restoredSessionStartRoute(
    restored: com.onlygoodthings.app.auth.AuthRestore,
    needsConsent: Boolean,
    needsProfile: Boolean,
    onboardingDone: Boolean,
): OgtRoute = when (restored) {
    com.onlygoodthings.app.auth.AuthRestore.LoggedOut -> OgtRoute.Landing
    com.onlygoodthings.app.auth.AuthRestore.LoginRequired -> OgtRoute.Login
    com.onlygoodthings.app.auth.AuthRestore.NeedsBiometric -> OgtRoute.UnlockSession
    com.onlygoodthings.app.auth.AuthRestore.FirstRun,
    com.onlygoodthings.app.auth.AuthRestore.Home -> authenticatedStartRoute(needsConsent, needsProfile, onboardingDone)
}

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
