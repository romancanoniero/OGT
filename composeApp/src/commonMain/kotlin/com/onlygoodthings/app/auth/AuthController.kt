package com.onlygoodthings.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onlygoodthings.app.data.OgtPreviewSession
import com.onlygoodthings.app.data.persistUnpublishedAnimal
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.platform.compressPhotoForUpload
import com.onlygoodthings.app.platform.readLocalFileBytes
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.data.remote.RestAnimalsRepository
import com.onlygoodthings.shared.data.remote.RestFeedRepository
import com.onlygoodthings.shared.data.remote.RestHonorRepository
import com.onlygoodthings.shared.data.remote.RestNoticeRepository
import com.onlygoodthings.shared.domain.UserRole
import com.onlygoodthings.shared.domain.titleCasePersonName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthController(
    private val db: OgtLocalDatabase,
    private val preview: OgtPreviewSession,
    val session: SessionStore = SessionStore().apply { apiBaseUrl = defaultApiBaseUrl() },
    private val allowDevTokens: Boolean = false,
) {
    private val platform = AuthPlatform()
    private val prefs = AuthPrefs()
    private val api = AuthSessionApi(session)
    val honor = RestHonorRepository(createPlatformHttpClient(), session)
    val notices = RestNoticeRepository(createPlatformHttpClient(), session)
    val animals = RestAnimalsRepository(createPlatformHttpClient(), session)
    val feed = RestFeedRepository(createPlatformHttpClient(), session)

    suspend fun updateMyLocation(latitude: Double, longitude: Double, accuracyMeters: Double?) {
        val ok = api.updateLocation(latitude, longitude, accuracyMeters)
        if (!ok) return
        val me = preview.overlayUser ?: return
        val updated = me.copy(latitude = latitude, longitude = longitude)
        val idx = db.users.indexOfFirst { it.id == me.id }
        if (idx >= 0) db.users[idx] = updated
        preview.overlayUser = updated
    }

    var pendingPhone by mutableStateOf<PendingPhoneAuth?>(null)
    /** Código del segundo factor que la cuenta ya tenía activado. */
    var pendingSecondFactor by mutableStateOf<PendingSecondFactor?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** La foto no se guardó. La pantalla ya volvió a la anterior: se puede elegir otra. */
    var profilePhotoError by mutableStateOf<String?>(null)

    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val framedUploads = mutableMapOf<String, Deferred<String>>()
    private var profilePhotoGeneration = 0
    private var profilePhotoInFlight = false
    private var profilePhotoBefore: String? = null

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
                pendingPhone = null
                finishSession(platform.signInEmail(contact.email, password), keep)
            }
            is AuthContact.Phone -> {
                pendingSecondFactor = null
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
        require(code.length == 6) { "El código debe tener 6 dígitos" }
        val factor = pendingSecondFactor
        if (factor != null) {
            finishSession(platform.confirmSecondFactor(code), keep = true)
            pendingSecondFactor = null
            return@wrap
        }
        val pending = pendingPhone ?: error("No hay verificación pendiente")
        finishSession(platform.confirmPhone(pending, code), keep = true)
        pending.displayName?.let { applyDisplayName(it) }
        pending.barrio?.let { applyBarrio(it) }
        pendingPhone = null
    }

    suspend fun resendOtp(): Boolean {
        val factor = pendingSecondFactor
        if (factor != null) {
            if (factor.kind != SecondFactorKind.Sms) return false
            return wrap { platform.resendSecondFactor() }
        }
        val pending = pendingPhone ?: return false
        return wrap {
            pendingPhone = platform.startPhoneAuth(pending.phoneE164, resend = true).copy(
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

    /** El teléfono tiene huella o rostro configurados. Sin eso el botón no se muestra. */
    suspend fun hasBiometric(): Boolean = platform.canUseBiometric()

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
        pendingSecondFactor = null
        _signedIn.value = false
        preview.currentUserId = com.onlygoodthings.shared.data.local.OgtIds.Mariana
        preview.overlayUser = null
        ensureDevBearer()
    }

    /** Token de lab cuando todavía no hay JWT Firebase, para grabar fichas en el backend local. */
    fun ensureDevBearer() {
        if (!allowDevTokens) return
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
            ?: if (allowDevTokens) "dev.${preview.me().firebaseUid.ifBlank { preview.me().id }}.USER" else null
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
        session.firebaseJwt = if (allowDevTokens) {
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
            photoUrl = profile?.photoUrl?.takeIf { it.isNotBlank() }
                ?: user.photoUrl?.takeIf { it.isNotBlank() }
                ?: existing.photoUrl,
        ) ?: LocalUser(
            id = user.firebaseUid,
            firebaseUid = user.firebaseUid,
            email = user.email,
            phoneE164 = user.phone,
            displayName = user.displayName ?: user.email ?: "Alguien de la comunidad",
            photoUrl = profile?.photoUrl?.takeIf { it.isNotBlank() } ?: user.photoUrl,
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
        preview.serverUserId = profile?.id ?: preview.serverUserId
        preview.overlayUser = local
        val photo = local.photoUrl
        if (!photo.isNullOrBlank()) {
            preview.applyRemoteProfilePhoto(profile?.id ?: local.id, photo)
        }
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

    /**
     * Sube la foto y la deja en users.photo_url.
     * El overlay se actualiza en el acto: cualquier pantalla que lea [OgtPreviewSession.me] la ve.
     */
    /**
     * Sube la foto de un post ya encuadrada. El llamador no la agrega si esto falla.
     */
    suspend fun uploadFramedPost(path: String): String {
        if (!allowDevTokens) {
            platform.idToken(true)?.let { session.firebaseJwt = it }
        }
        ensureDevBearer()
        val bytes = readLocalFileBytes(path) ?: error("No se pudo leer la foto")
        if (bytes.isEmpty()) error("La foto está vacía")
        if (bytes.size > 8 * 1024 * 1024) error("La foto supera 8 MB")
        val uploaded = api.uploadImage("post.jpg", "image/jpeg", bytes)
        OgtMediaCache.put(uploaded.url, bytes)
        return uploaded.url
    }

    /** La miniatura ya está en pantalla. Esta subida es la misma si se pide dos veces. */
    fun enqueueFramedUpload(id: String, path: String): Deferred<String> {
        framedUploads[id]?.let { existing ->
            if (existing.isActive || existing.isCompleted) return existing
        }
        val task = jobs.async { uploadFramedPost(path) }
        framedUploads[id] = task
        task.invokeOnCompletion {
            if (framedUploads[id] === task) framedUploads.remove(id)
        }
        return task
    }

    suspend fun awaitFramedUpload(id: String, path: String): String =
        enqueueFramedUpload(id, path).await()

    /** Sigue después de cerrar la pantalla que lo disparó. */
    fun enqueue(block: suspend () -> Unit) {
        jobs.launch { block() }
    }

    /** Espera la subida que ya se mostró. Si falla, queda el archivo local. */
    suspend fun readyDraft(items: List<OgtPickedMedia>): List<OgtPickedMedia> = items.map { item ->
        if (!item.fromDevice || item.kind != MediaKind.IMAGE || !item.uploadedUrl.isNullOrBlank()) {
            item
        } else {
            val url = runCatching { awaitFramedUpload(item.id, item.path) }.getOrNull()
            if (url != null) item.copy(uploadedUrl = url) else item
        }
    }

    /**
     * Pinta el archivo local en todas las pantallas y sube después.
     * Si la subida falla, vuelve la foto anterior y [profilePhotoError] deja reintentar.
     */
    fun stageAndSaveProfilePhoto(path: String) {
        val me = preview.me()
        if (!profilePhotoInFlight) {
            profilePhotoBefore = me.photoUrl?.takeIf { !it.startsWith("file:") }
                ?: preview.serverUserId?.let { preview.livePhotos[it] }?.takeIf { !it.startsWith("file:") }
        }
        val local = if (path.startsWith("file:") || path.startsWith("http")) path else "file://$path"
        preview.applyRemoteProfilePhoto(me.id, local)
        val generation = ++profilePhotoGeneration
        profilePhotoInFlight = true
        profilePhotoError = null
        jobs.launch {
            try {
                val url = saveProfilePhoto(path, paint = false)
                if (generation != profilePhotoGeneration) return@launch
                preview.applyRemoteProfilePhoto(preview.me().id, url)
                profilePhotoBefore = null
                profilePhotoInFlight = false
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Throwable) {
                if (generation != profilePhotoGeneration) return@launch
                profilePhotoInFlight = false
                preview.restoreProfilePhoto(profilePhotoBefore)
                profilePhotoBefore = null
                profilePhotoError = failure.message ?: "No se pudo guardar la foto. Volvé a elegirla."
            }
        }
    }

    suspend fun saveProfilePhoto(path: String, paint: Boolean = true): String {
        val ready = compressPhotoForUpload(path) ?: path
        val bytes = readLocalFileBytes(ready)
            ?: error("No se pudo leer la foto")
        if (bytes.isEmpty()) error("La foto está vacía")
        if (bytes.size > 8 * 1024 * 1024) error("La foto supera 8 MB")
        if (!allowDevTokens) {
            platform.idToken(true)?.let { session.firebaseJwt = it }
        }
        ensureDevBearer()
        val me = preview.overlayUser ?: preview.me()
        val name = me.displayName.trim()
        if (name.length < 2) error("El nombre es muy corto")
        val filename = "perfil.jpg"
        val contentType = "image/jpeg"
        val uploaded = api.uploadImage(filename, contentType, bytes)
        val card = api.updateProfile(
            displayName = name,
            photoUrl = uploaded.url,
            language = preview.language.code,
            barrio = me.barrio,
            publicProfileVisible = preview.publicProfileVisible,
            showExactMatchLocation = preview.showExactMatchLocation,
            animalAlertPush = preview.animalAlertPush,
            skillAlertPush = preview.skillAlertPush,
            parkingRadarSounds = preview.parkingRadarSounds,
            radarEnabled = preview.radarEnabled,
            carbonSaveMode = preview.carbonSaveMode,
        )
        val url = card.photoUrl?.takeIf { it.isNotBlank() } ?: uploaded.url
        OgtMediaCache.put(url, bytes)
        preview.serverUserId = card.userId
        if (paint) preview.applyRemoteProfilePhoto(card.userId, url)
        return url
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
        } catch (e: NeedsSecondFactor) {
            pendingPhone = null
            pendingSecondFactor = e.pending
            error = null
            false
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
