package com.onlygoodthings.app.auth

/**
 * Host que implementa Swift (FirebaseAuth + Google + Facebook + biometría)
 * y que Kotlin consume desde [AuthPlatform].
 */
interface IosAuthHost {
    fun currentUser(): AuthUser?
    fun idToken(forceRefresh: Boolean, onResult: (String?, String?) -> Unit)
    fun signInEmail(email: String, password: String, onResult: (AuthUser?, String?) -> Unit)
    fun signUpEmail(email: String, password: String, displayName: String, onResult: (AuthUser?, String?) -> Unit)
    fun sendPasswordReset(email: String, onResult: (String?) -> Unit)
    fun startPhoneAuth(phoneE164: String, onResult: (String?, String?) -> Unit)
    fun confirmPhone(verificationId: String, code: String, onResult: (AuthUser?, String?) -> Unit)
    fun signInGoogle(onResult: (AuthUser?, String?) -> Unit)
    fun signInFacebook(onResult: (AuthUser?, String?) -> Unit)
    fun unlockBiometric(onResult: (Boolean) -> Unit)
    fun canUseBiometric(): Boolean
    fun signOut()
}

object IosAuthRuntime {
    var host: IosAuthHost? = null
}

fun iosAuthUser(
    uid: String,
    email: String?,
    phone: String?,
    displayName: String?,
    photoUrl: String?,
): AuthUser = AuthUser(
    firebaseUid = uid,
    email = email,
    phone = phone,
    displayName = displayName,
    photoUrl = photoUrl,
)
