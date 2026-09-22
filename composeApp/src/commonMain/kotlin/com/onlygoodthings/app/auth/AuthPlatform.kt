package com.onlygoodthings.app.auth

/** Puente nativo a Firebase Auth y biometría. */
expect class AuthPlatform() {
    suspend fun currentUser(): AuthUser?
    suspend fun idToken(forceRefresh: Boolean = false): String?
    suspend fun signInEmail(email: String, password: String): AuthUser
    suspend fun signUpEmail(email: String, password: String, displayName: String): AuthUser
    suspend fun sendPasswordReset(email: String)
    suspend fun startPhoneAuth(phoneE164: String): PendingPhoneAuth
    suspend fun confirmPhone(pending: PendingPhoneAuth, code: String): AuthUser
    suspend fun signInGoogle(): AuthUser
    suspend fun signInFacebook(): AuthUser
    suspend fun unlockBiometric(): Boolean
    suspend fun canUseBiometric(): Boolean
    suspend fun signOut()
}

expect class AuthPrefs() {
    var keepSession: Boolean
    var biometricEnabled: Boolean
    var onboardingDone: Boolean
    var parkedLatitude: Double
    var parkedLongitude: Double
    var parkedAtEpochMs: Long
    var parkedHeading: Float
    var parkedManeuver: String
    /** `es` (LATAM) o `en`. */
    var appLanguage: String
    var vehicleMake: String
    var vehicleColor: String
    var vehicleColorHex: String
    var vehiclePlate: String
    var parkedAddress: String
    /** True solo si confirmó “ESTACIONE” / ParkHere. */
    var parkedSetByUser: Boolean
    var vehiclesJson: String
    var selectedVehicleId: String
    /** Token de mención de honor pendiente de reivindicar. */
    var pendingHonorToken: String
    /** Mentions de honor emitidas en este dispositivo. */
    var honorsJson: String
    /** Fichas de adopción/perdido publicadas en este dispositivo. */
    var publishedAnimalsJson: String
    /** Último feed social visto: se muestra hasta que llegue el REST. */
    var feedCacheJson: String
    /** Ya se leyó el referrer de Play para no reaplicar el claim. */
    var honorReferrerConsumed: Boolean
    /** El vecino encendió el GPS. Sin esto no hay tracking ni ping al servidor. */
    var gpsEnabled: Boolean
    /** `while` o `always`. Alcance pedido al sistema. */
    var locationScope: String
}

expect fun defaultApiBaseUrl(): String
