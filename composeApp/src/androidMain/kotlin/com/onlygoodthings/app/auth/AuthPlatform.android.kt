package com.onlygoodthings.app.auth

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.MultiFactorSession
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.PhoneMultiFactorInfo
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TotpMultiFactorInfo
import com.google.firebase.auth.UserProfileChangeRequest
import com.onlygoodthings.app.AndroidAuthHost
import com.onlygoodthings.app.OgtApplication
import com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val GOOGLE_WEB_CLIENT_ID =
    "521063054927-2i8k3qkmh48p5h3qp3nqaunt5t7j4sj0.apps.googleusercontent.com"

actual class AuthPlatform actual constructor() {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private var pendingResolver: MultiFactorResolver? = null
    private var pendingVerificationId: String? = null
    private var pendingEnrollmentId: String? = null
    private var pendingKind: String? = null
    private var pendingInstantCredential: PhoneAuthCredential? = null
    private var phoneResendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var mfaResendToken: PhoneAuthProvider.ForceResendingToken? = null

    actual suspend fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    actual suspend fun idToken(forceRefresh: Boolean): String? =
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.token

    actual suspend fun signInEmail(email: String, password: String): AuthUser = runAuth {
        auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase no devolvió usuario")
    }

    actual suspend fun confirmSecondFactor(code: String): AuthUser {
        val resolver = pendingResolver
            ?: throw AuthException("El paso extra venció. Volvé a ingresar la contraseña.")
        val assertion = when (pendingKind) {
            "totp" -> {
                val enrollmentId = pendingEnrollmentId
                    ?: throw AuthException("El paso extra venció. Volvé a ingresar la contraseña.")
                TotpMultiFactorGenerator.getAssertionForSignIn(enrollmentId, code)
            }
            else -> {
                val instant = pendingInstantCredential
                val credential = instant ?: PhoneAuthProvider.getCredential(
                    pendingVerificationId
                        ?: throw AuthException("El paso extra venció. Volvé a ingresar la contraseña."),
                    code,
                )
                PhoneMultiFactorGenerator.getAssertion(credential)
            }
        }
        return runAuth {
            val user = resolver.resolveSignIn(assertion).await().user ?: error("Código inválido")
            clearSecondFactor()
            user
        }
    }

    actual suspend fun resendSecondFactor() {
        val resolver = pendingResolver
            ?: throw AuthException("El paso extra venció. Volvé a ingresar la contraseña.")
        val phone = resolver.hints.filterIsInstance<PhoneMultiFactorInfo>().firstOrNull()
            ?: throw AuthException("Este código sale de tu app de autenticación.")
        pendingVerificationId = sendSecondFactorSms(phone, resolver.session, resend = true)
    }

    actual suspend fun signUpEmail(email: String, password: String, displayName: String): AuthUser = runAuth {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("No se pudo crear la cuenta")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        user.reload().await()
        user
    }

    actual suspend fun sendPasswordReset(email: String) {
        runCatching { auth.sendPasswordResetEmail(email).await() }
            .getOrElse { throw mapFirebase(it) }
    }

    actual suspend fun startPhoneAuth(phoneE164: String, resend: Boolean): PendingPhoneAuth {
        val activity = AndroidAuthHost.requireActivity()
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    auth.signInWithCredential(credential).addOnCompleteListener { task ->
                        if (!cont.isActive) return@addOnCompleteListener
                        if (task.isSuccessful) {
                            cont.resume(PendingPhoneAuth(verificationId = "auto", phoneE164 = phoneE164))
                        } else {
                            cont.resumeWithException(mapFirebase(task.exception))
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) cont.resumeWithException(mapFirebase(e))
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    phoneResendToken = token
                    if (cont.isActive) {
                        cont.resume(PendingPhoneAuth(verificationId = verificationId, phoneE164 = phoneE164))
                    }
                }
            }
            if (!resend) phoneResendToken = null
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .apply {
                    if (resend) phoneResendToken?.let { setForceResendingToken(it) }
                }
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    actual suspend fun confirmPhone(pending: PendingPhoneAuth, code: String): AuthUser = runAuth {
        if (pending.verificationId == "auto") {
            return@runAuth auth.currentUser ?: error("La verificación automática no dejó sesión")
        }
        val credential = PhoneAuthProvider.getCredential(pending.verificationId, code)
        auth.signInWithCredential(credential).await().user ?: error("Código inválido")
    }

    actual suspend fun signInGoogle(): AuthUser {
        val activity = AndroidAuthHost.requireActivity()
        val manager = CredentialManager.create(activity)
        // El botón de Google debe abrir el selector. GetGoogleIdOption es One Tap y,
        // sin cuentas en el emulador, falla sin mostrar UI.
        val response = runCatching {
            val button = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build()
            manager.getCredential(
                activity,
                GetCredentialRequest.Builder().addCredentialOption(button).build(),
            )
        }.recoverCatching { first ->
            if (first is GetCredentialCancellationException || first is NoCredentialException) throw first
            val oneTap = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            manager.getCredential(
                activity,
                GetCredentialRequest.Builder().addCredentialOption(oneTap).build(),
            )
        }.getOrElse { throw mapGoogleOpen(it) }
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            return runAuth {
                auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                    .await().user ?: error("Google no devolvió usuario")
            }
        }
        throw AuthException("La cuenta de Google no devolvió un token válido")
    }


    actual suspend fun unlockBiometric(): Boolean {
        val activity = AndroidAuthHost.requireActivity()
        if (activity !is FragmentActivity) {
            throw AuthException("La biometría requiere una Activity compat")
        }
        if (!canUseBiometric()) return false
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onAuthenticationFailed() {
                        // se reintenta hasta error/cancel
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Ingresar a la comunidad")
                    .setSubtitle("Huella o rostro para desbloquear tu sesión")
                    .setNegativeButtonText("Cancelar")
                    .build(),
            )
        }
    }

    actual suspend fun canUseBiometric(): Boolean {
        val ctx = OgtApplication.instance
        val manager = BiometricManager.from(ctx)
        val flags = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        return manager.canAuthenticate(flags) == BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun signOut() {
        clearSecondFactor()
        auth.signOut()
    }

    private fun clearSecondFactor() {
        pendingResolver = null
        pendingVerificationId = null
        pendingEnrollmentId = null
        pendingKind = null
        pendingInstantCredential = null
        mfaResendToken = null
    }

    /** Arma el reto solo si Firebase ya lo exigió. No inscribe un factor nuevo. */
    private suspend fun prepareSecondFactor(error: FirebaseAuthMultiFactorException): FirebaseUser? {
        val resolver = error.resolver
        val phone = resolver.hints.filterIsInstance<PhoneMultiFactorInfo>().firstOrNull()
        val totp = resolver.hints.filterIsInstance<TotpMultiFactorInfo>().firstOrNull()
        clearSecondFactor()
        pendingResolver = resolver
        when {
            phone != null -> {
                pendingKind = "sms"
                val verificationId = sendSecondFactorSms(phone, resolver.session, resend = false)
                if (verificationId == "auto-mfa") {
                    val credential = pendingInstantCredential
                        ?: throw AuthException("No se pudo completar el paso extra")
                    val user = resolver.resolveSignIn(PhoneMultiFactorGenerator.getAssertion(credential))
                        .await().user ?: error("No se pudo completar el paso extra")
                    clearSecondFactor()
                    return user
                }
                pendingVerificationId = verificationId
                throw NeedsSecondFactor(PendingSecondFactor(phone.phoneNumber.orEmpty(), SecondFactorKind.Sms))
            }
            totp != null -> {
                pendingKind = "totp"
                pendingEnrollmentId = totp.uid
                throw NeedsSecondFactor(PendingSecondFactor(totp.displayName.orEmpty(), SecondFactorKind.Totp))
            }
            else -> throw AuthException("Este segundo factor no está soportado en la app.")
        }
    }

    private suspend fun sendSecondFactorSms(
        phone: PhoneMultiFactorInfo,
        session: MultiFactorSession,
        resend: Boolean,
    ): String {
        val activity = AndroidAuthHost.requireActivity()
        if (pendingResolver == null) {
            throw AuthException("El paso extra venció. Volvé a ingresar la contraseña.")
        }
        if (!resend) mfaResendToken = null
        val token = if (resend) mfaResendToken else null
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    pendingInstantCredential = credential
                    if (cont.isActive) cont.resume("auto-mfa")
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) cont.resumeWithException(mapFirebase(e))
                }

                override fun onCodeSent(verificationId: String, forceToken: PhoneAuthProvider.ForceResendingToken) {
                    mfaResendToken = forceToken
                    if (cont.isActive) cont.resume(verificationId)
                }
            }
            val options = PhoneAuthOptions.newBuilder(auth)
                .setActivity(activity)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setMultiFactorSession(session)
                .setMultiFactorHint(phone)
                .setCallbacks(callbacks)
                .apply { if (token != null) setForceResendingToken(token) }
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    private suspend fun signInOAuth(providerId: String, scopes: List<String>): AuthUser {
        val activity = AndroidAuthHost.requireActivity()
        val built = OAuthProvider.newBuilder(providerId).setScopes(scopes).build()
        return runAuth {
            auth.startActivityForSignInWithProvider(activity, built).await().user
                ?: error("No se completó el ingreso con $providerId")
        }
    }

    private suspend fun runAuth(block: suspend () -> FirebaseUser): AuthUser =
        try {
            block().toAuthUser()
        } catch (e: NeedsSecondFactor) {
            throw e
        } catch (t: Throwable) {
            val mfa = generateSequence(t) { it.cause }.filterIsInstance<FirebaseAuthMultiFactorException>().firstOrNull()
            if (mfa != null) {
                prepareSecondFactor(mfa)?.toAuthUser() ?: throw AuthException("No se pudo completar el paso extra")
            } else {
                throw mapFirebase(t)
            }
        }
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    firebaseUid = uid,
    email = email,
    phone = phoneNumber,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
)

private fun mapGoogleOpen(error: Throwable): AuthException = when (error) {
    is GetCredentialCancellationException -> AuthException("Cancelaste el ingreso con Google")
    is NoCredentialException -> AuthException(
        "No hay una cuenta de Google en este emulador. Agregala en Ajustes → Cuentas → Agregar cuenta",
    )
    else -> AuthException(
        "No se pudo abrir Google Sign-In. En el emulador tiene que haber una cuenta Google en Ajustes.",
        error,
    )
}

private fun mapFirebase(error: Throwable?): AuthException {
    val code = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
    val message = when (code) {
        "ERROR_INVALID_EMAIL" -> "El correo no es válido"
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Correo o contraseña incorrectos"
        "ERROR_USER_NOT_FOUND" -> "No hay una cuenta con esos datos"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya está registrado"
        "ERROR_WEAK_PASSWORD" -> "La contraseña es demasiado débil"
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Probá más tarde"
        "ERROR_INVALID_VERIFICATION_CODE" -> "El código no es válido"
        "ERROR_SESSION_EXPIRED" -> "El código SMS venció. Pedí uno nuevo"
        "ERROR_QUOTA_EXCEEDED" -> "Se agotó la cuota de SMS de Firebase"
        "ERROR_OPERATION_NOT_ALLOWED" -> "Este método de ingreso no está habilitado en Firebase"
        "ERROR_SECOND_FACTOR_REQUIRED" ->
            "No pudimos abrir el paso extra de esta cuenta. Volvé a intentar."
        else -> {
            val raw = error?.message.orEmpty()
            if (raw.contains("second factor", ignoreCase = true)) {
                "No pudimos abrir el paso extra de esta cuenta. Volvé a intentar."
            } else {
                raw.ifBlank { "No se pudo autenticar" }
            }
        }
    }
    return AuthException(message, error)
}

actual class AuthPrefs actual constructor() {
    private val prefs = OgtApplication.instance.getSharedPreferences("ogt_auth", 0)
    actual var keepSession: Boolean
        get() = prefs.getBoolean("keep", true)
        set(value) { prefs.edit().putBoolean("keep", value).apply() }
    actual var biometricEnabled: Boolean
        get() = prefs.getBoolean("bio", false)
        set(value) { prefs.edit().putBoolean("bio", value).apply() }
    actual var onboardingDone: Boolean
        get() = prefs.getBoolean("onboard", false)
        set(value) { prefs.edit().putBoolean("onboard", value).apply() }
    actual var parkedLatitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("park_lat", 0L))
        set(value) { prefs.edit().putLong("park_lat", java.lang.Double.doubleToRawLongBits(value)).apply() }
    actual var parkedLongitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("park_lng", 0L))
        set(value) { prefs.edit().putLong("park_lng", java.lang.Double.doubleToRawLongBits(value)).apply() }
    actual var parkedAtEpochMs: Long
        get() = prefs.getLong("park_at", 0L)
        set(value) { prefs.edit().putLong("park_at", value).apply() }
    actual var parkedHeading: Float
        get() = prefs.getFloat("park_hdg", 0f)
        set(value) { prefs.edit().putFloat("park_hdg", value).apply() }
    actual var parkedManeuver: String
        get() = prefs.getString("park_man", "").orEmpty()
        set(value) { prefs.edit().putString("park_man", value).apply() }
    actual var appLanguage: String
        get() = prefs.getString("lang", "es").orEmpty().ifBlank { "es" }
        set(value) { prefs.edit().putString("lang", value).apply() }
    actual var vehicleMake: String
        get() = prefs.getString("car_make", "").orEmpty()
        set(value) { prefs.edit().putString("car_make", value).apply() }
    actual var vehicleColor: String
        get() = prefs.getString("car_color", "").orEmpty()
        set(value) { prefs.edit().putString("car_color", value).apply() }
    actual var vehicleColorHex: String
        get() = prefs.getString("car_hex", "#6B7280").orEmpty().ifBlank { "#6B7280" }
        set(value) { prefs.edit().putString("car_hex", value).apply() }
    actual var vehiclePlate: String
        get() = prefs.getString("car_plate", "").orEmpty()
        set(value) { prefs.edit().putString("car_plate", value).apply() }
    actual var parkedAddress: String
        get() = prefs.getString("park_addr", "").orEmpty()
        set(value) { prefs.edit().putString("park_addr", value).apply() }
    actual var parkedSetByUser: Boolean
        get() = prefs.getBoolean("park_set", false)
        set(value) { prefs.edit().putBoolean("park_set", value).apply() }
    actual var vehiclesJson: String
        get() = prefs.getString("cars_json", "").orEmpty()
        set(value) { prefs.edit().putString("cars_json", value).apply() }
    actual var selectedVehicleId: String
        get() = prefs.getString("car_sel", "").orEmpty()
        set(value) { prefs.edit().putString("car_sel", value).apply() }
    actual var pendingHonorToken: String
        get() = prefs.getString("honor_token", "").orEmpty()
        set(value) { prefs.edit().putString("honor_token", value).apply() }
    actual var honorsJson: String
        get() = prefs.getString("honors_json", "").orEmpty()
        set(value) { prefs.edit().putString("honors_json", value).apply() }
    actual var publishedAnimalsJson: String
        get() = prefs.getString("animals_json", "").orEmpty()
        set(value) { prefs.edit().putString("animals_json", value).apply() }
    actual var feedCacheJson: String
        get() = prefs.getString("feed_json", "").orEmpty()
        set(value) { prefs.edit().putString("feed_json", value).apply() }
    actual var honorReferrerConsumed: Boolean
        get() = prefs.getBoolean("honor_referrer_done", false)
        set(value) { prefs.edit().putBoolean("honor_referrer_done", value).apply() }
    actual var gpsEnabled: Boolean
        get() = prefs.getBoolean("gps_on", false)
        set(value) { prefs.edit().putBoolean("gps_on", value).apply() }
    actual var locationScope: String
        get() = prefs.getString("gps_scope", "while").orEmpty().ifBlank { "while" }
        set(value) { prefs.edit().putString("gps_scope", value).apply() }
}

actual fun defaultApiBaseUrl(): String = OGT_DEFAULT_API_BASE
