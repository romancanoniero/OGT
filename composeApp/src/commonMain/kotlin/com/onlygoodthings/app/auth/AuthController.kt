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
import com.onlygoodthings.shared.data.remote.RestFeedRepository
import com.onlygoodthings.shared.data.remote.RestHonorRepository
import com.onlygoodthings.shared.data.remote.RestNoticeRepository
import com.onlygoodthings.shared.domain.UserRole
import com.onlygoodthings.shared.domain.titleCasePersonName
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
    var appLockEnabled by mutableStateOf(prefs.appLockEnabled)
        private set
    init { prefs.noticeOwnerId = "" }
    val account = AuthSessionApi(session, onSessionExpired = { rejectedToken ->
        check(session.firebaseJwt == rejectedToken) { "La sesión cambió" }
        expireSession()
    }) {
        ownerScopedAccountToken({ preview.overlayUser?.firebaseUid.orEmpty() }) { uid ->
            if (allowDevTokens) session.firebaseJwt else {
                check(platform.currentUser()?.firebaseUid == uid) { "La sesión cambió" }
                sessionToken(false)
            }
        }.also { session.firebaseJwt = it }
    }
    private val api = account
    private val settingsMutex = kotlinx.coroutines.sync.Mutex()
    private val accountWorkScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Default)

    /** Survives leaving the editor, while never borrowing a different account's token. */
    fun notifyPublicationMentions(owner:String,postId:String,recipients:List<String>) {
        if(owner.isBlank() || preview.currentUserId!=owner)return
        val bearer=session.firebaseJwt ?: return
        val base=session.apiBaseUrl
        accountWorkScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(10_000) {
                for(recipient in recipients.distinct().filter { it!=owner }) {
                    if(preview.currentUserId!=owner)break
                    try { api.notifyPublishedMention(recipient,postId,bearer,base) }
                    catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                    catch(_:Exception) { /* Publication is already confirmed; notices are best effort. */ }
                }
            }
        }
    }

    suspend fun refreshSettings() {
        val owner=preview.currentUserId
        val settings=api.settings()
        check(owner.isNotBlank() && preview.currentUserId==owner) { "La sesión cambió" }
        preview.applyAccountSettings(settings)
    }

    suspend fun updateSettings(owner: String, change: (com.onlygoodthings.shared.domain.AccountSettings) -> com.onlygoodthings.shared.domain.AccountSettings) {
        settingsMutex.lock()
        try {
            check(owner.isNotBlank() && preview.currentUserId == owner) { "La sesión cambió" }
            val json = kotlinx.serialization.json.Json { encodeDefaults = true }
            val before = json.encodeToJsonElement(com.onlygoodthings.shared.domain.AccountSettings.serializer(), preview.accountSettings) as kotlinx.serialization.json.JsonObject
            val next = change(preview.accountSettings).copy(timeZone = kotlinx.datetime.TimeZone.currentSystemDefault().id)
            val after = json.encodeToJsonElement(com.onlygoodthings.shared.domain.AccountSettings.serializer(), next) as kotlinx.serialization.json.JsonObject
            val saved = api.saveSettings(kotlinx.serialization.json.JsonObject(after.filter { (key, value) -> before[key] != value }))
            check(preview.currentUserId == owner) { "La sesión cambió" }
            preview.applyAccountSettings(saved)
        } finally { settingsMutex.unlock() }
    }
    val stories = com.onlygoodthings.app.data.StoriesController(session,prefs) { sessionToken(false) }
    val honor = RestHonorRepository(createPlatformHttpClient(), session)
    val notices = RestNoticeRepository(createPlatformHttpClient(), session)
    val animals = RestAnimalsRepository(createPlatformHttpClient(), session)
    val feed = RestFeedRepository(createPlatformHttpClient(), session)
    val feedTimeline = com.onlygoodthings.app.data.FeedTimeline(
        db=db,
        prepare={
            if(allowDevTokens)ensureDevBearer() else {
                val owner=preview.currentUserId
                val token=sessionToken(false)
                check(owner.isNotBlank() && preview.currentUserId==owner) { "La sesión cambió" }
                session.firebaseJwt=token
            }
        },
        load={ query -> feed.loadFeed("0",40,query.mode,query.family) },
        visible=feed::visiblePostIds,
        loadPost=feed::getPost,
        reconcile=preview::reconcileFeedVisibility,
        persist=preview::persistSocialFeed,
        prefetch={ com.onlygoodthings.app.media.OgtMediaCache.prefetch(it) },
    )
    val postInvitations = com.onlygoodthings.shared.data.remote.RestPostInvitations(createPlatformHttpClient(), session)

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
    private val secondFactorSignIn = SecondFactorSignIn(
        begin = platform::beginSecondFactor,
        verify = platform::confirmSecondFactor,
        openSession = { user, _ -> finishSession(user) },
        cancelNative = platform::cancelSecondFactor,
    )
    val secondFactors: List<AuthSecondFactor> get() = secondFactorSignIn.factors
    val selectedSecondFactor: AuthSecondFactor? get() = secondFactorSignIn.selected
    val secondFactorExpired: Boolean get() = secondFactorSignIn.expired

    private fun beginPrimarySignIn() {
        secondFactorSignIn.clear()
    }

    suspend fun chooseSecondFactor(index: Int): Boolean {
        var authenticated = false
        return wrap { authenticated = secondFactorSignIn.choose(index) } && authenticated
    }

    suspend fun confirmSecondFactor(code: String): Boolean = wrap { secondFactorSignIn.confirm(code) }

    fun cancelSecondFactor() {
        secondFactorSignIn.clear()
        error = null
    }
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var requiresLogin by mutableStateOf(false)
        private set
    var needsCommunityConsent by mutableStateOf(true)
        private set
    val needsProfileCompletion: Boolean get() = preview.overlayUser?.let {
        profileNeedsCompletion(it.displayName, preview.accountSettings.barrio, it.firebaseUid, it.photoUrl)
    } ?: true

    suspend fun completeProfile(name: String, barrio: String): Boolean = wrap {
        val me = preview.overlayUser ?: error("Iniciá sesión para continuar")
        val titled = titleCasePersonName(name.trim())
        require(!profileNeedsCompletion(titled, barrio, me.firebaseUid, me.photoUrl)) { "Completá tu nombre, tu zona o comunidad y tu foto" }
        require(barrio.trim().length <= 80) { "La zona o comunidad debe tener hasta 80 caracteres" }
        val card = api.completeProfile(titled, barrio.trim(), me.photoUrl.orEmpty())
        check(preview.currentUserId == me.id) { "La sesión cambió" }
        val photo = card.photoUrl ?: me.photoUrl
        db.applyUserLive(card.userId, card.displayName, photo)
        preview.overlayUser = me.copy(displayName = card.displayName, photoUrl = photo, barrio = barrio.trim())
        refreshSettings()
    }

    suspend fun acceptCommunityConsent(): Boolean = wrap {
        val owner = preview.currentUserId
        check(owner.isNotBlank()) { "Iniciá sesión para continuar" }
        val saved = api.acceptCommunityConsent()
        check(preview.currentUserId == owner) { "La sesión cambió" }
        check(saved.current) { "No se pudo confirmar la aceptación" }
        needsCommunityConsent = false
    }

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn

    suspend fun restore(): AuthRestore {
        error = null
        return try {
            restoreSavedAuthSession(
                explicitlySignedOut = prefs.sessionSignedOut,
                appLockEnabled = prefs.appLockEnabled,
                currentUser = platform::currentUser,
                openSession = { finishSession(it) },
                onboardingDone = { prefs.onboardingDone },
                onSessionExpired = { if (!requiresLogin) expireSession() },
            )
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            error = failure.message ?: "No se pudo recuperar la sesión"
            throw failure
        }
    }

    suspend fun signInIdentifier(identifier: String, password: String): Boolean = wrap {
        beginPrimarySignIn()
        when (val contact = parseContact(identifier)) {
            is AuthContact.Email -> {
                if (password.isBlank()) error("Ingresá tu contraseña")
                finishSession(platform.signInEmail(contact.email, password))
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
        beginPrimarySignIn()
        require(name.isNotBlank()) { "Ingresá tu nombre" }
        val titled = titleCasePersonName(name.trim())
        when (val contact = parseContact(identifier)) {
            is AuthContact.Email -> {
                require(password.length >= 6) { "La contraseña debe tener al menos 6 caracteres" }
                finishSession(platform.signUpEmail(contact.email, password, titled))
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
        finishSession(platform.confirmPhone(pending, code))
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
        beginPrimarySignIn()
        val user = when (provider) {
            "google" -> platform.signInGoogle()
            "apple" -> platform.signInApple()
            else -> error("Proveedor no soportado")
        }
        finishSession(user)
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

    suspend fun canUseBiometric(): Boolean = runCatching { platform.canUseBiometric() }.getOrElse { false }

    suspend fun setAppLockEnabled(enabled: Boolean): Boolean = wrap {
        updateSavedSessionLock(
            enabled = enabled,
            currentOwner = { if (_signedIn.value && !prefs.sessionSignedOut) platform.currentUser()?.firebaseUid else null },
            canAuthenticate = platform::canUseBiometric,
            authenticate = platform::unlockBiometric,
            save = { prefs.appLockEnabled = it; appLockEnabled = it },
        )
    }

    suspend fun unlockWithBiometric(): Boolean = wrap {
        if (!platform.canUseBiometric()) {
            throw AuthException("Este dispositivo no tiene huella o rostro configurados")
        }
        val user = platform.currentUser()
            ?: throw AuthException("Primero iniciá sesión para activar el ingreso biométrico")
        if (!platform.unlockBiometric()) throw AuthException("No se pudo validar la biometría")
        prefs.biometricEnabled = true
        finishSession(user)
    }

    /** Borra la fila de OGT y el contenido de esta cuenta, y después cierra la sesión local. */
    suspend fun setUserBlocked(userId:String,blocked:Boolean) {
        feed.setUserBlocked(userId,blocked)
        if(blocked) {
            db.posts.removeAll { it.authorUserId==userId }
            db.inbox.removeAll { it.peerUserId==userId }
            db.comments.removeAll { it.authorUserId==userId }
            db.anecdotes.removeAll { it.authorUserId==userId }
            db.follows.removeAll { (it.followerId==preview.currentUserId && it.followedId==userId) || (it.followerId==userId && it.followedId==preview.currentUserId) }
            stories.excludeAuthor(userId)
            preview.persistSocialFeed()
        }
        db.bumpFeed();db.bumpSocial();stories.refresh()
    }

    suspend fun deleteAccount(): Boolean = wrap {
        if (session.firebaseJwt.isNullOrBlank()) error("Iniciá sesión para borrar la cuenta")
        platform.prepareAccountDeletion()
        session.firebaseJwt = sessionToken(true)
        api.deleteAccount()
        preview.deleteCurrentSettingsAccount()
        preview.deleteCurrentParkingAccount()
        signOut()
    }

    suspend fun uploadPostMedia(items: List<com.onlygoodthings.app.platform.OgtPickedMedia>, altText: String): List<com.onlygoodthings.shared.domain.PostMediaItem> {
        ensureDevBearer()
        val owner = preview.currentUserId
        val refreshed = if (allowDevTokens) null else sessionToken(false)
        check(owner == preview.currentUserId) { "La sesión cambió. Volvé a intentarlo." }
        if (!refreshed.isNullOrBlank()) session.firebaseJwt = refreshed
        val token = session.firebaseJwt ?: error("Iniciá sesión para publicar.")
        val media = com.onlygoodthings.app.platform.uploadPostMedia(items, altText,
            read = { path -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.onlygoodthings.app.platform.readLocalFileBytes(path) } },
            upload = { filename, bytes ->
                check(owner == preview.currentUserId) { "La sesión cambió. Volvé a intentarlo." }
                api.uploadMedia(token, filename, bytes)
            },
        )
        check(owner == preview.currentUserId) { "La sesión cambió. Volvé a intentarlo." }
        return media
    }

    suspend fun updateProfilePhoto(path: String) {
        val ownerId = preview.currentUserId
        val token = session.firebaseJwt ?: error("Iniciá sesión para guardar tu foto")
        val name = preview.me().displayName
        val before = preview.me().photoUrl
        preview.applyProfilePhoto(path)
        try {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.onlygoodthings.app.platform.readLocalFileBytes(path)
                    ?: error("No se pudo leer la foto. Elegila nuevamente.")
            }
            val uploaded = api.uploadProfilePhoto(token, path.substringAfterLast('/'), bytes)
            val card = api.updateProfilePhoto(token, name, uploaded.url)
            if (preview.currentUserId == ownerId) {
                db.applyUserLive(card.userId, card.displayName, card.photoUrl)
                preview.applyProfilePhoto(card.photoUrl ?: uploaded.url)
            }
        } catch (failure: Throwable) {
            if (preview.currentUserId == ownerId) {
                val current = preview.me().copy(photoUrl = before)
                val index = db.users.indexOfFirst { it.id == ownerId }
                if (index >= 0) db.users[index] = current
                preview.overlayUser = current
            }
            throw failure
        }
    }

    suspend fun signOut() {
        prefs.sessionSignedOut = true
        error = null
        needsCommunityConsent = true
        com.onlygoodthings.app.media.StoreReviewMedia.clear()
        com.onlygoodthings.shared.realtime.OgtSdk.shutdown()
        prefs.noticeOwnerId = ""
        com.onlygoodthings.app.notify.clearSystemNotices()
        val previousToken=com.onlygoodthings.app.notify.currentPushToken()
        val previousBearer=session.firebaseJwt
        val previousBase=session.apiBaseUrl
        feedTimeline.clear()
        stories.clear()
        runCatching { platform.signOut() }
        session.firebaseJwt = null
        if(session.apiBaseUrl==com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE+"/store-review")session.apiBaseUrl=com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE
        pendingPhone = null
        secondFactorSignIn.clear()
        _signedIn.value = false
        preview.switchParkingAccount("")
        preview.switchSettingsAccount("")
        preview.currentUserId = ""
        preview.overlayUser = null
        ensureDevBearer()
        if(previousToken!=null && previousBearer!=null)accountWorkScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(3000) {
                try { api.unregisterNoticeToken(previousToken,previousBearer,previousBase) }
                catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                catch(_:Exception) { /* Best effort offline; a new registration also reassigns this token. */ }
            }
        }
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
        // Restoration provisions the server account before any authenticated preload.
        if (!allowDevTokens && !_signedIn.value) return
        val me = preview.me()
        db.claimPublishedAnimals(me.aliases() + me.id, me.id)
        runCatching { animals.openListings() }.getOrNull().orEmpty().forEach { remote ->
            db.upsertRemoteAnimal(remote)
        }
        db.unpublishedAnimalPosts().forEach { post ->
            runCatching {
                persistUnpublishedAnimal(db, animals, me, post, { preview.currentUserId == me.id }, ::uploadPostMedia)
            }
        }
    }

    suspend fun refreshAnimalSightings(listingId: String) {
        val owner=preview.currentUserId
        val rows=animals.sightings(listingId)
        check(owner.isNotBlank() && preview.currentUserId==owner) { "La sesión cambió" }
        db.applyRemoteSightings(listingId,rows)
    }

    /** JWT Firebase o token de lab para el handshake de db-kmp-sdk. */
    fun realtimeTokenProvider(): suspend (Boolean) -> String? = { force ->
        (if (allowDevTokens) platform.idToken(force) else sessionToken(force))
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

    private suspend fun expireSession() {
        signOut()
        requiresLogin = true
    }

    private suspend fun sessionToken(forceRefresh: Boolean): String = renewedSessionToken(
        owner = { platform.currentUser()?.firebaseUid },
        load = { platform.idToken(forceRefresh) },
        onExpired = { expireSession() },
    )

    private suspend fun finishSession(user: AuthUser) {
        val token = sessionToken(true)
        val bearer = if (allowDevTokens) "dev.${user.firebaseUid}.USER" else token
        val profile = api.openSession(bearer, user.firebaseUid)
        session.firebaseJwt = bearer
        attachLocal(user, profile)
        needsCommunityConsent = !api.communityConsent().current
        refreshSettings()
        val zone=kotlinx.datetime.TimeZone.currentSystemDefault().id
        if(preview.accountSettings.timeZone!=zone)preview.applyAccountSettings(api.saveSettings(kotlinx.serialization.json.buildJsonObject { put("timeZone",kotlinx.serialization.json.JsonPrimitive(zone)) }))
        val reviewTransport=session.apiBaseUrl==com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE+"/store-review"
        com.onlygoodthings.app.media.StoreReviewMedia.configure(reviewTransport) {
            check(platform.currentUser()?.firebaseUid==user.firebaseUid) { "La sesión cambió" }
            sessionToken(false)
        }
        com.onlygoodthings.shared.realtime.OgtSdk.restart(com.onlygoodthings.shared.realtime.gatewayEndpointFromApiBase(session.apiBaseUrl),realtimeTokenProvider())
        stories.start(preview.currentUserId)
        feedTimeline.start(preview.currentUserId)
        ensureDevBearer()
        prefs.sessionSignedOut = false
        _signedIn.value = true
        requiresLogin = false
        error = null
    }

    private fun attachLocal(user: AuthUser, profile: com.onlygoodthings.shared.domain.UserProfile?) {
        val existing = db.users.firstOrNull {
            it.firebaseUid == user.firebaseUid ||
                (user.email != null && it.email == user.email)
        }
        val local = existing?.copy(
            id = profile?.id ?: existing.id,
            firebaseUid = user.firebaseUid,
            displayName = profile?.displayName ?: user.displayName ?: existing.displayName,
            nickname = profile?.nickname ?: existing.nickname,
            email = user.email ?: existing.email,
            phoneE164 = user.phone ?: existing.phoneE164,
            photoUrl = profile?.photoUrl ?: user.photoUrl ?: existing.photoUrl,
            communityPoints = profile?.communityPoints ?: existing.communityPoints,
            role = profile?.role ?: existing.role,
            levelLabel = com.onlygoodthings.shared.domain.communityLevelLabel(profile?.communityPoints ?: existing.communityPoints),
        ) ?: LocalUser(
            id = profile?.id ?: user.firebaseUid,
            firebaseUid = user.firebaseUid,
            email = user.email,
            phoneE164 = user.phone,
            displayName = profile?.displayName ?: user.displayName ?: user.email ?: "Alguien de la comunidad",
            nickname = profile?.nickname,
            photoUrl = profile?.photoUrl ?: user.photoUrl,
            role = profile?.role ?: UserRole.USER,
            communityPoints = profile?.communityPoints ?: 0,
            inviteCode = profile?.inviteCode ?: (user.firebaseUid.take(8).uppercase() + "-GOOD"),
            barrio = "Tu comunidad",
            levelLabel = com.onlygoodthings.shared.domain.communityLevelLabel(profile?.communityPoints ?: 0),
            honorTag = "Red GoodThings",
            latitude = -34.588,
            longitude = -58.430,
        )
        if (existing == null) db.users.add(local) else {
            val idx = db.users.indexOfFirst { it.id == existing.id }
            if (idx >= 0) db.users[idx] = local
        }
        preview.switchParkingAccount(user.firebaseUid)
        preview.switchSettingsAccount(user.firebaseUid)
        preview.currentUserId = local.id
        preview.overlayUser = local
        db.claimPublishedAnimals(
            setOfNotNull(user.firebaseUid, profile?.id, existing?.id, local.id),
            local.id,
        )
    }

    private suspend fun applyBarrio(barrio: String) {
        val me = preview.overlayUser ?: return
        if (barrio.isNotBlank()) updateSettings(me.id) { it.copy(barrio = barrio.trim()) }
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
        if (busy) return false
        busy = true
        error = null
        return try {
            block()
            true
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (challenge: AuthSecondFactorRequired) {
            secondFactorSignIn.require(challenge.factors, keepSession = true)
            error = null
            false
        } catch (expired: AuthSessionExpired) {
            if (!requiresLogin) expireSession()
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
