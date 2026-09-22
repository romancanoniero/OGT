package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE
import platform.Foundation.NSUserDefaults
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

actual class AuthPlatform actual constructor() {
    private fun host(): IosAuthHost =
        IosAuthRuntime.host ?: throw AuthException("Firebase iOS todavía no se inicializó")

    actual suspend fun currentUser(): AuthUser? = host().currentUser()

    actual suspend fun idToken(forceRefresh: Boolean): String? = suspendCancellableCoroutine { cont ->
        host().idToken(forceRefresh) { token, err ->
            if (err != null) cont.resumeWithException(AuthException(err))
            else cont.resume(token)
        }
    }

    actual suspend fun signInEmail(email: String, password: String): AuthUser =
        awaitUser { host().signInEmail(email, password, it) }

    actual suspend fun signUpEmail(email: String, password: String, displayName: String): AuthUser =
        awaitUser { host().signUpEmail(email, password, displayName, it) }

    actual suspend fun sendPasswordReset(email: String) = suspendCancellableCoroutine { cont ->
        host().sendPasswordReset(email) { err ->
            if (err != null) cont.resumeWithException(AuthException(err))
            else cont.resume(Unit)
        }
    }

    actual suspend fun startPhoneAuth(phoneE164: String): PendingPhoneAuth =
        suspendCancellableCoroutine { cont ->
            host().startPhoneAuth(phoneE164) { verificationId, err ->
                when {
                    err != null -> cont.resumeWithException(AuthException(err))
                    verificationId != null -> cont.resume(PendingPhoneAuth(verificationId, phoneE164))
                    else -> cont.resumeWithException(AuthException("No llegó el SMS"))
                }
            }
        }

    actual suspend fun confirmPhone(pending: PendingPhoneAuth, code: String): AuthUser {
        if (pending.verificationId == "auto") {
            return host().currentUser() ?: throw AuthException("La verificación automática no dejó sesión")
        }
        return awaitUser { host().confirmPhone(pending.verificationId, code, it) }
    }

    actual suspend fun signInGoogle(): AuthUser = awaitUser { host().signInGoogle(it) }

    actual suspend fun signInFacebook(): AuthUser = awaitUser { host().signInFacebook(it) }

    actual suspend fun unlockBiometric(): Boolean = suspendCancellableCoroutine { cont ->
        host().unlockBiometric { cont.resume(it) }
    }

    actual suspend fun canUseBiometric(): Boolean = host().canUseBiometric()

    actual suspend fun signOut() {
        host().signOut()
    }

    private suspend fun awaitUser(block: ((AuthUser?, String?) -> Unit) -> Unit): AuthUser =
        suspendCancellableCoroutine { cont ->
            block { user, err ->
                when {
                    err != null -> cont.resumeWithException(AuthException(err))
                    user != null -> cont.resume(user)
                    else -> cont.resumeWithException(AuthException("No se pudo autenticar"))
                }
            }
        }
}

actual class AuthPrefs actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults
    actual var keepSession: Boolean
        get() = defaults.boolForKey("ogt.keep")
        set(value) { defaults.setBool(value, forKey = "ogt.keep") }
    actual var biometricEnabled: Boolean
        get() = defaults.boolForKey("ogt.bio")
        set(value) { defaults.setBool(value, forKey = "ogt.bio") }
    actual var onboardingDone: Boolean
        get() = defaults.boolForKey("ogt.onboard")
        set(value) { defaults.setBool(value, forKey = "ogt.onboard") }
    actual var parkedLatitude: Double
        get() = defaults.doubleForKey("ogt.park.lat")
        set(value) { defaults.setDouble(value, forKey = "ogt.park.lat") }
    actual var parkedLongitude: Double
        get() = defaults.doubleForKey("ogt.park.lng")
        set(value) { defaults.setDouble(value, forKey = "ogt.park.lng") }
    actual var parkedAtEpochMs: Long
        get() = (defaults.objectForKey("ogt.park.at") as? platform.Foundation.NSNumber)?.longLongValue ?: 0L
        set(value) { defaults.setObject(platform.Foundation.NSNumber(longLong = value), forKey = "ogt.park.at") }
    actual var parkedHeading: Float
        get() = defaults.floatForKey("ogt.park.hdg")
        set(value) { defaults.setFloat(value, forKey = "ogt.park.hdg") }
    actual var parkedManeuver: String
        get() = defaults.stringForKey("ogt.park.man").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.park.man") }
    actual var appLanguage: String
        get() = defaults.stringForKey("ogt.lang")?.ifBlank { null } ?: "es"
        set(value) { defaults.setObject(value, forKey = "ogt.lang") }
    actual var vehicleMake: String
        get() = defaults.stringForKey("ogt.car.make").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.car.make") }
    actual var vehicleColor: String
        get() = defaults.stringForKey("ogt.car.color").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.car.color") }
    actual var vehicleColorHex: String
        get() = defaults.stringForKey("ogt.car.hex")?.ifBlank { null } ?: "#6B7280"
        set(value) { defaults.setObject(value, forKey = "ogt.car.hex") }
    actual var vehiclePlate: String
        get() = defaults.stringForKey("ogt.car.plate").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.car.plate") }
    actual var parkedAddress: String
        get() = defaults.stringForKey("ogt.park.addr").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.park.addr") }
    actual var parkedSetByUser: Boolean
        get() = defaults.boolForKey("ogt.park.set")
        set(value) { defaults.setBool(value, forKey = "ogt.park.set") }
    actual var vehiclesJson: String
        get() = defaults.stringForKey("ogt.cars.json").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.cars.json") }
    actual var selectedVehicleId: String
        get() = defaults.stringForKey("ogt.car.sel").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.car.sel") }
    actual var pendingHonorToken: String
        get() = defaults.stringForKey("ogt.honor.token").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.honor.token") }
    actual var honorsJson: String
        get() = defaults.stringForKey("ogt.honors.json").orEmpty()
        set(value) { defaults.setObject(value, forKey = "ogt.honors.json") }
    actual var publishedAnimalsJson: String
        get() = defaults.stringForKey("ogt.animals.json").orEmpty()
        set(value) {
            defaults.setObject(value, forKey = "ogt.animals.json")
            defaults.synchronize()
        }
    actual var feedCacheJson: String
        get() = defaults.stringForKey("ogt.feed.json").orEmpty()
        set(value) {
            defaults.setObject(value, forKey = "ogt.feed.json")
            defaults.synchronize()
        }
    actual var honorReferrerConsumed: Boolean
        get() = defaults.boolForKey("ogt.honor.referrer")
        set(value) { defaults.setBool(value, forKey = "ogt.honor.referrer") }
    actual var gpsEnabled: Boolean
        get() = defaults.boolForKey("ogt.gps")
        set(value) { defaults.setBool(value, forKey = "ogt.gps") }
    actual var locationScope: String
        get() = defaults.stringForKey("ogt.gps.scope")?.ifBlank { null } ?: "while"
        set(value) { defaults.setObject(value, forKey = "ogt.gps.scope") }
}

actual fun defaultApiBaseUrl(): String = OGT_DEFAULT_API_BASE
