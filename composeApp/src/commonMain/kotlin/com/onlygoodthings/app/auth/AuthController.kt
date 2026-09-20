package com.onlygoodthings.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onlygoodthings.app.data.OgtPreviewSession
import com.onlygoodthings.app.data.persistUnpublishedAnimal
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.data.remote.RestAnimalsRepository
import com.onlygoodthings.shared.data.remote.RestHonorRepository
import com.onlygoodthings.shared.data.remote.RestNoticeRepository
import com.onlygoodthings.shared.domain.UserRole
import com.onlygoodthings.shared.domain.titleCasePersonName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthController(
    private val db: OgtLocalDatabase,
    private val preview: OgtPreviewSession,
    val session: SessionStore = SessionStore().apply { apiBaseUrl = defaultApiBaseUrl() },
) {
    private val platform = AuthPlatform()
    private val prefs = AuthPrefs()
    private val api = AuthSessionApi(session)
    val honor = RestHonorRepository(createPlatformHttpClient(), session)
    val notices = RestNoticeRepository(createPlatformHttpClient(), session)
    val animals = RestAnimalsRepository(createPlatformHttpClient(), session)

    var pendingPhone by mutableStateOf<PendingPhoneAuth?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn

    suspend fun restore(): AuthRestore {
        val user = platform.currentUser() ?: return AuthRestore.LoggedOut
        if (prefs.biometricEnabled) return AuthRestore.NeedsBiometric
        if (!prefs.keepSession) {
            platform.signOut()
            return AuthRestore.LoggedOut
        }
        runCatching { finishSession(user) }
        return if (prefs.onboardingDone) AuthRestore.Home else AuthRestore.FirstRun
    }

    suspend fun signInIdentifier(identifier: String, password: String, keep: Boolean): Boolean = wrap {
        when (val contact = parseContact(identifier)) {
            is AuthContact.Email -> {
                if (password.isBlank()) error("Ingresá tu contraseña")
                finishSession(platform.signInEmail(contact.email, password), keep)
            }
            is AuthContact.Phone -> {
                pendingPhone = platform.startPhoneAuth(contact.e164)
            }
        }
    }

    suspend fun signUp(
        name: String,
        identifier: String,
        password: String,
        barrio: String,
    ): Boolean = wrap {
        require(name.isNotBlank()) { "Ingresá tu nombre" }
        val titled = titleCasePersonName(name.trim())
        when (val contact = parseContact(identifier)) {
            is AuthContact.Email -> {
                require(password.length >= 6) { "La contraseña debe tener al menos 6 caracteres" }
                finishSession(platform.signUpEmail(contact.email, password, titled), keep = true)
                applyBarrio(barrio)
            }
            is AuthContact.Phone -> {
                pendingPhone = platform.startPhoneAuth(contact.e164).copy(
                    displayName = titled,
                    password = password.takeIf { it.length >= 6 },
                    barrio = barrio,
                    isNewUser = true,
                )
            }
        }
    }

    suspend fun confirmOtp(code: String): Boolean = wrap {
        val pending = pendingPhone ?: error("No hay verificación pendiente")
        require(code.length == 6) { "El código debe tener 6 dígitos" }
        finishSession(platform.confirmPhone(pending, code), keep = true)
        pending.displayName?.let { applyDisplayName(it) }
        pending.barrio?.let { applyBarrio(it) }
        pendingPhone = null
    }

    suspend fun resendOtp(): Boolean {
        val pending = pendingPhone ?: return false
        return wrap {
            pendingPhone = platform.startPhoneAuth(pending.phoneE164).copy(
                displayName = pending.displayName,
                password = pending.password,
                barrio = pending.barrio,
                isNewUser = pending.isNewUser,
            )
        }
    }

    suspend fun signInProvider(provider: String): Boolean = wrap {
        val user = when (provider) {
            "google" -> platform.signInGoogle()
            "apple" -> {
                require(showsAppleSignIn()) { "Sign in with Apple solo está disponible en iPhone o iPad" }
                platform.signInApple()
            }
            "facebook" -> platform.signInFacebook()
            else -> error("Proveedor no soportado")
        }
        finishSession(user, keep = true)
    }

    suspend fun recover(identifier: String, byMail: Boolean): Boolean = wrap {
        val contact = parseContact(identifier)
        when {
            byMail && contact is AuthContact.Email -> platform.sendPasswordReset(contact.email)
            contact is AuthContact.Phone -> pendingPhone = platform.startPhoneAuth(contact.e164)
            contact is AuthContact.Email -> platform.sendPasswordReset(contact.email)
            else -> error("Ingresá un correo o un teléfono válido")
        }
    }

    suspend fun unlockWithBiometric(): Boolean {
        if (!platform.canUseBiometric()) {
            error = "Este dispositivo no tiene huella o rostro configurados"
            return false
        }
        val user = platform.currentUser()
        if (user == null) {
            error = "Primero iniciá sesión para activar el ingreso biométrico"
            return false
        }
        return if (platform.unlockBiometric()) {
            prefs.biometricEnabled = true
            finishSession(user, keep = true)
            true
        } else {
            error = "No se pudo validar la biometría"
            false
        }
    }

    suspend fun signOut() {
        runCatching { platform.signOut() }
        session.firebaseJwt = null
        pendingPhone = null
        _signedIn.value = false
        preview.currentUserId = com.onlygoodthings.shared.data.local.OgtIds.Mariana
        preview.overlayUser = null
        ensureDevBearer()
    }

    /** Token de lab cuando todavía no hay JWT Firebase, para grabar fichas en el backend local. */
    fun ensureDevBearer() {
        val uid = preview.me().firebaseUid.ifBlank { preview.me().id }
        val lab = "dev.$uid.USER"
        val current = session.firebaseJwt
        // La VPS de demo es HTTP: Firebase Admin no verifica el JWT real.
        if (current.isNullOrBlank() ||
            (session.apiBaseUrl.startsWith("http://") && current.startsWith("dev.") != true)
        ) {
            session.firebaseJwt = lab
        }
    }

    suspend fun hydratePublishedAnimals(db: OgtLocalDatabase) {
        ensureDevBearer()
        val me = preview.me()
        db.claimPublishedAnimals(me.aliases() + me.id, me.id)
        runCatching { animals.openListings() }.getOrNull().orEmpty().forEach { remote ->
            db.upsertRemoteAnimal(remote)
        }
        db.unpublishedAnimalPosts().forEach { post ->
            runCatching {
                persistUnpublishedAnimal(db, animals, me, post)
            }
        }
    }

    /** JWT Firebase o token de lab para el handshake de db-kmp-sdk. */
    fun realtimeTokenProvider(): suspend (Boolean) -> String? = { force ->
        platform.idToken(force)
            ?: session.firebaseJwt
            ?: "dev.${preview.me().firebaseUid.ifBlank { preview.me().id }}.USER"
    }

    fun markOnboardingDone() {
        prefs.onboardingDone = true
    }

    fun hasFinishedOnboarding(): Boolean = prefs.onboardingDone

    fun enableBiometricAfterLogin() {
        prefs.biometricEnabled = true
    }

    private suspend fun finishSession(user: AuthUser, keep: Boolean = true) {
        prefs.keepSession = keep
        val token = platform.idToken(true) ?: error("No se pudo obtener el token Firebase")
        session.firebaseJwt = if (session.apiBaseUrl.startsWith("http://")) {
            "dev.${user.firebaseUid}.USER"
        } else {
            token
        }
        val profile = api.openSession(session.firebaseJwt ?: token)
        attachLocal(user, profile)
        ensureDevBearer()
        _signedIn.value = true
        error = null
    }

    private fun attachLocal(user: AuthUser, profile: com.onlygoodthings.shared.domain.UserProfile?) {
        val existing = db.users.firstOrNull {
            it.firebaseUid == user.firebaseUid ||
                (user.email != null && it.email == user.email)
        }
        val local = existing?.copy(
            displayName = user.displayName ?: existing.displayName,
            email = user.email ?: existing.email,
            phoneE164 = user.phone ?: existing.phoneE164,
            photoUrl = user.photoUrl ?: existing.photoUrl,
        ) ?: LocalUser(
            id = user.firebaseUid,
            firebaseUid = user.firebaseUid,
            email = user.email,
            phoneE164 = user.phone,
            displayName = user.displayName ?: user.email ?: "Alguien de la comunidad",
            photoUrl = user.photoUrl,
            role = profile?.role ?: UserRole.USER,
            communityPoints = profile?.communityPoints ?: 0,
            inviteCode = profile?.inviteCode ?: (user.firebaseUid.take(8).uppercase() + "-GOOD"),
            barrio = "Tu comunidad",
            levelLabel = "Comunidad nueva",
            honorTag = "Red GoodThings",
            latitude = -34.588,
            longitude = -58.430,
        )
        if (existing == null) db.users.add(local) else {
            val idx = db.users.indexOfFirst { it.id == existing.id }
            if (idx >= 0) db.users[idx] = local
        }
        preview.currentUserId = local.id
        preview.overlayUser = local
        db.claimPublishedAnimals(
            setOfNotNull(user.firebaseUid, profile?.id, existing?.id, local.id),
            local.id,
        )
    }

    private fun applyBarrio(barrio: String) {
        val me = preview.overlayUser ?: return
        val updated = me.copy(barrio = barrio.ifBlank { me.barrio })
        val idx = db.users.indexOfFirst { it.id == me.id }
        if (idx >= 0) db.users[idx] = updated
        preview.overlayUser = updated
    }

    private fun applyDisplayName(name: String) {
        val me = preview.overlayUser ?: return
        val updated = me.copy(displayName = titleCasePersonName(name))
        val idx = db.users.indexOfFirst { it.id == me.id }
        if (idx >= 0) db.users[idx] = updated
        preview.overlayUser = updated
    }

    private suspend fun wrap(block: suspend () -> Unit): Boolean {
        busy = true
        error = null
        return try {
            block()
            true
        } catch (e: AuthException) {
            error = e.message
            false
        } catch (e: IllegalArgumentException) {
            error = e.message
            false
        } catch (e: Throwable) {
            error = e.message ?: "No se pudo completar la autenticación"
            false
        } finally {
            busy = false
        }
    }
}
