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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneMultiFactorInfo
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
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

actual fun authLabHintsEnabled(): Boolean = com.onlygoodthings.app.BuildConfig.DEBUG && com.onlygoodthings.app.BuildConfig.APPLICATION_ID != "com.onlygoodthings.app.qa"

actual class AuthPlatform actual constructor() {
    private val auth: FirebaseAuth? get() = try {
        FirebaseAuth.getInstance()
    } catch (t: Throwable) {
        null
    }
    private var factorResolver: MultiFactorResolver? = null
    private var factorIndex: Int? = null
    private var factorVerificationId: String? = null
    private var automaticFactorCredential: PhoneAuthCredential? = null
    private var factorGeneration = 0

    actual fun cancelSecondFactor() {
        factorGeneration++
        factorResolver = null
        factorIndex = null
        factorVerificationId = null
        automaticFactorCredential = null
    }

    actual suspend fun beginSecondFactor(index: Int): Boolean {
        val resolver = factorResolver ?: throw AuthException("La verificación venció. Volvé a ingresar.")
        val hint = resolver.hints.getOrNull(index) ?: throw AuthException("Elegí una verificación disponible")
        factorGeneration++
        val generation = factorGeneration
        factorIndex = index
        factorVerificationId = null
        automaticFactorCredential = null
        if (hint.factorId == TotpMultiFactorGenerator.FACTOR_ID) return true
        if (hint !is PhoneMultiFactorInfo) throw AuthException("Ese segundo factor no está disponible en esta app")
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (cont.isActive && generation == factorGeneration) {
                        factorVerificationId = id
                        cont.resume(true)
                    }
                }
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (cont.isActive && generation == factorGeneration) {
                        automaticFactorCredential = credential
                        cont.resume(false)
                    }
                }
                override fun onVerificationFailed(error: FirebaseException) {
                    if (cont.isActive && generation == factorGeneration) cont.resumeWithException(mapFirebase(error))
                }
            }
            val a = auth ?: throw AuthException("Autenticación no disponible")
            val options = PhoneAuthOptions.newBuilder(a)
                .setActivity(AndroidAuthHost.requireActivity())
                .setMultiFactorHint(hint)
                .setMultiFactorSession(resolver.session)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    actual suspend fun confirmSecondFactor(code: String): AuthUser {
        val resolver = factorResolver ?: throw AuthException("La verificación venció. Volvé a ingresar.")
        val hint = factorIndex?.let { resolver.hints.getOrNull(it) }
            ?: throw AuthException("Elegí una verificación disponible")
        val assertion = when (hint.factorId) {
            TotpMultiFactorGenerator.FACTOR_ID -> {
                require(validSecondFactorCode(code)) { "Ingresá los seis dígitos del código" }
                TotpMultiFactorGenerator.getAssertionForSignIn(hint.uid, code)
            }
            PhoneMultiFactorGenerator.FACTOR_ID -> {
                val credential = automaticFactorCredential ?: run {
                    require(validSecondFactorCode(code)) { "Ingresá los seis dígitos del código" }
                    val id = factorVerificationId ?: throw AuthException("Solicitá el código SMS nuevamente")
                    PhoneAuthProvider.getCredential(id, code)
                }
                PhoneMultiFactorGenerator.getAssertion(credential)
            }
            else -> throw AuthException("Ese segundo factor no está disponible en esta app")
        }
        return runAuth { resolver.resolveSignIn(assertion).await().user ?: error("No se pudo confirmar el ingreso") }
    }

    actual suspend fun currentUser(): AuthUser? = auth?.currentUser?.toAuthUser()

    actual suspend fun idToken(forceRefresh: Boolean): String? = try {
        auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token
    } catch (failure: com.google.firebase.auth.FirebaseAuthException) {
        if (isExpiredSessionCode(failure.errorCode)) throw AuthSessionExpired(failure)
        throw failure
    }

    actual suspend fun signInEmail(email: String, password: String): AuthUser = runAuth {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        a.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase no devolvió usuario")
    }

    actual suspend fun signUpEmail(email: String, password: String, displayName: String): AuthUser = runAuth {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        val user = a.createUserWithEmailAndPassword(email, password).await().user
            ?: error("No se pudo crear la cuenta")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        user.reload().await()
        user
    }

    actual suspend fun sendPasswordReset(email: String) {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        runCatching { a.sendPasswordResetEmail(email).await() }
            .getOrElse { throw mapFirebase(it) }
    }

    actual suspend fun startPhoneAuth(phoneE164: String): PendingPhoneAuth {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        val activity = AndroidAuthHost.requireActivity()
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    a.signInWithCredential(credential).addOnCompleteListener { task ->
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
                    if (cont.isActive) {
                        cont.resume(PendingPhoneAuth(verificationId = verificationId, phoneE164 = phoneE164))
                    }
                }
            }
            val a = auth ?: throw AuthException("Autenticación no disponible")
            val options = PhoneAuthOptions.newBuilder(a)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    actual suspend fun confirmPhone(pending: PendingPhoneAuth, code: String): AuthUser = runAuth {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        if (pending.verificationId == "auto") {
            return@runAuth a.currentUser ?: error("La verificación automática no dejó sesión")
        }
        val credential = PhoneAuthProvider.getCredential(pending.verificationId, code)
        a.signInWithCredential(credential).await().user ?: error("Código inválido")
    }

    actual suspend fun signInGoogle(): AuthUser {
        val a = auth ?: throw AuthException("Autenticación no disponible")
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
                a.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                    .await().user ?: error("Google no devolvió usuario")
            }
        }
        throw AuthException("La cuenta de Google no devolvió un token válido")
    }


    actual suspend fun signInApple(): AuthUser = signInOAuth("apple.com", listOf("email", "name"))

    actual suspend fun prepareAccountDeletion() {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        val user=a.currentUser ?: throw AuthException("Iniciá sesión para borrar la cuenta")
        if(user.providerData.none { it.providerId=="apple.com" })return
        val provider=OAuthProvider.newBuilder("apple.com").setScopes(listOf("email","name")).build()
        val result=user.startActivityForReauthenticateWithProvider(AndroidAuthHost.requireActivity(),provider).await()
        check(result.user?.uid==user.uid && a.currentUser?.uid==user.uid) { "La sesión cambió. Volvé a solicitar la baja." }
        val accessToken=(result.credential as? OAuthCredential)?.accessToken
            ?: throw AuthException("Apple no devolvió la autorización necesaria para la baja. Tu cuenta se conserva.")
        a.revokeAccessToken(accessToken).await()
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
        val ctx = runCatching { OgtApplication.instance }.getOrNull() ?: return false
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
        auth?.signOut()
    }

    private suspend fun signInOAuth(providerId: String, scopes: List<String>): AuthUser {
        val a = auth ?: throw AuthException("Autenticación no disponible")
        val activity = AndroidAuthHost.requireActivity()
        val built = OAuthProvider.newBuilder(providerId).setScopes(scopes).build()
        return runAuth {
            a.startActivityForSignInWithProvider(activity, built).await().user
                ?: error("No se completó el ingreso con $providerId")
        }
    }

    private suspend fun runAuth(block: suspend () -> FirebaseUser): AuthUser = try {
        block().toAuthUser()
    } catch (cancel: kotlinx.coroutines.CancellationException) {
        throw cancel
    } catch (challenge: FirebaseAuthMultiFactorException) {
        cancelSecondFactor()
        factorResolver = challenge.resolver
        val factors = challenge.resolver.hints.mapIndexedNotNull { index, hint ->
            when (hint.factorId) {
                TotpMultiFactorGenerator.FACTOR_ID -> AuthSecondFactor(index, "totp", hint.displayName ?: "Aplicación autenticadora")
                PhoneMultiFactorGenerator.FACTOR_ID -> AuthSecondFactor(index, "phone", "SMS · ••••${(hint as? PhoneMultiFactorInfo)?.phoneNumber.orEmpty().takeLast(4)}")
                else -> null
            }
        }
        if (factors.isEmpty()) throw AuthException("No hay un segundo factor compatible para completar el ingreso")
        throw AuthSecondFactorRequired(factors)
    } catch (failure: Exception) {
        throw mapFirebase(failure)
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

// El id del proyecto de Firebase no se muestra. El nombre visible es Only Good Things.
private fun sinIdDeProyecto(message: String?): String {
    val text = message.orEmpty()
    if (text.contains("521063054927") || text.contains("goodthings-55612") || text.contains("project-")) {
        return "No se pudo completar el ingreso a Only Good Things."
    }
    return text.ifBlank { "No se pudo autenticar" }
}

private fun mapFirebase(error: Throwable?): AuthException {
    val code = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
    if (isExpiredSessionCode(code)) return AuthSessionExpired(error)
    val message = when (code) {
        "ERROR_INVALID_EMAIL" -> "El correo no es válido"
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Correo o contraseña incorrectos"
        "ERROR_USER_NOT_FOUND" -> "No hay una cuenta con esos datos"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya está registrado"
        "ERROR_WEAK_PASSWORD" -> "La contraseña es demasiado débil"
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Probá más tarde"
        "ERROR_INVALID_VERIFICATION_CODE" -> "El código de verificación no es válido"
        "ERROR_SESSION_EXPIRED" -> "El código SMS venció. Pedí uno nuevo"
        "ERROR_QUOTA_EXCEEDED" -> "Se agotó la cuota de SMS de Firebase"
        "ERROR_OPERATION_NOT_ALLOWED" -> "Este método de ingreso no está habilitado en Firebase"
        else -> sinIdDeProyecto(error?.message)
    }
    return AuthException(message, error)
}

actual class AuthPrefs actual constructor() {
    private val prefs = try {
        OgtApplication.instance.getSharedPreferences("ogt_auth", 0)
    } catch (t: Throwable) {
        InMemorySharedPreferences()
    }
    actual var sessionSignedOut: Boolean
        get() = prefs.getBoolean("signed_out", false)
        // Persist logout before clearing Firebase so a process restart cannot revive it.
        set(value) { prefs.edit().putBoolean("signed_out", value).commit() }
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
    actual var parkingAccountsJson: String
        get() = prefs.getString("parking_accounts_json", "").orEmpty()
        set(value) { prefs.edit().putString("parking_accounts_json", value).apply() }
    actual var settingsAccountsJson: String
        get() = prefs.getString("settings_accounts_json", "").orEmpty()
        set(value) { prefs.edit().putString("settings_accounts_json", value).apply() }
    actual var noticeOwnerId: String
        get() = prefs.getString("notice_owner", "").orEmpty()
        set(value) { prefs.edit().putString("notice_owner", value).apply() }
    actual var noticeSettingsJson: String
        get() = prefs.getString("notice_settings", "").orEmpty()
        set(value) { prefs.edit().putString("notice_settings", value).apply() }
    actual var selectedVehicleId: String
        get() = prefs.getString("car_sel", "").orEmpty()
        set(value) { prefs.edit().putString("car_sel", value).apply() }
    actual var pendingInvitationToken: String
        get() = prefs.getString("invitation_token", "").orEmpty()
        set(value) { prefs.edit().putString("invitation_token", value).apply() }
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
    actual var storyDraftsJson: String
        get() = prefs.getString("story_drafts_json", "").orEmpty()
        set(value) { prefs.edit().putString("story_drafts_json", value).apply() }
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

private class InMemorySharedPreferences : android.content.SharedPreferences {
    private val map = java.util.Collections.synchronizedMap(HashMap<String, Any?>())

    private class Editor(
        private val map: MutableMap<String, Any?>,
        private val onCommit: () -> Unit = {}
    ) : android.content.SharedPreferences.Editor {
        private val modified = HashMap<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor {
            if (value == null) modified[key] = this else modified[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): android.content.SharedPreferences.Editor {
            if (values == null) modified[key] = this else modified[key] = HashSet(values)
            return this
        }
        override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor {
            modified[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor {
            modified[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor {
            modified[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor {
            modified[key] = value
            return this
        }
        override fun remove(key: String): android.content.SharedPreferences.Editor {
            modified[key] = this
            return this
        }
        override fun clear(): android.content.SharedPreferences.Editor {
            clear = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            synchronized(map) {
                if (clear) map.clear()
                for ((k, v) in modified) {
                    if (v === this) map.remove(k) else map[k] = v
                }
            }
            onCommit()
        }
    }

    override fun getAll(): Map<String, *> = HashMap(map)

    override fun getString(key: String, defValue: String?): String? =
        (map[key] as? String) ?: defValue

    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? =
        (map[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        (map[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (map[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (map[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (map[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): android.content.SharedPreferences.Editor = Editor(map)

    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {}
}

actual fun defaultApiBaseUrl(): String =
    if(com.onlygoodthings.app.BuildConfig.DEBUG && com.onlygoodthings.app.BuildConfig.APPLICATION_ID=="com.onlygoodthings.app.qa")"http://${com.onlygoodthings.app.BuildConfig.OGT_QA_HOST}:19083" else OGT_DEFAULT_API_BASE
actual fun appVersionLabel(): String = "${com.onlygoodthings.app.BuildConfig.VERSION_NAME} (${com.onlygoodthings.app.BuildConfig.VERSION_CODE})"
