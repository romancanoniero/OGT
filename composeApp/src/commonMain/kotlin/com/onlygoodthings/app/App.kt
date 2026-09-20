package com.onlygoodthings.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.onlygoodthings.app.auth.AuthRestore
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.OgtPreviewStore
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.LocationPermissionState
import com.onlygoodthings.app.map.isGranted
import com.onlygoodthings.app.map.rememberOgtLocation
import com.onlygoodthings.app.notify.rememberOgtNotify
import com.onlygoodthings.app.platform.OgtIncomingLinks
import com.onlygoodthings.app.platform.isStorePreviewLaunch
import com.onlygoodthings.app.platform.storePreviewRoute
import com.onlygoodthings.app.platform.storePreviewScreenName
import com.onlygoodthings.app.platform.readHonorClipboard
import com.onlygoodthings.app.platform.startHonorInstallReferrer
import com.onlygoodthings.shared.domain.honorAppLink
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.app.ui.screens.LocationScopeDialog
import com.onlygoodthings.app.ui.screens.ParkingManeuverDialog
import com.onlygoodthings.app.ui.screens.ParkedPromptDialog
import com.onlygoodthings.app.ui.screens.YieldApproachDialog
import com.onlygoodthings.shared.domain.DriveParkingDetector
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.MotionSample
import com.onlygoodthings.shared.domain.ParkedPresenceAction
import com.onlygoodthings.shared.domain.ParkedPresenceTick
import com.onlygoodthings.shared.domain.ParkingManeuver
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingStatus
import com.onlygoodthings.shared.domain.decideParkedPresence
import com.onlygoodthings.shared.domain.shouldAskYieldOnApproach
import com.onlygoodthings.shared.realtime.currentEpochMs
import androidx.compose.runtime.CompositionLocalProvider
import com.onlygoodthings.app.nav.ComposePostKind
import com.onlygoodthings.app.nav.OgtBackHandler
import com.onlygoodthings.app.nav.OgtBottomTabs
import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.app.theme.LushImpactTheme
import com.onlygoodthings.shared.data.local.OgtIds
import com.onlygoodthings.app.ui.components.OgtBottomBar
import com.onlygoodthings.app.ui.screens.AnimatedSvgScreen
import com.onlygoodthings.app.ui.screens.AdoptApplicationScreen
import com.onlygoodthings.app.ui.screens.AnimalsScreen
import com.onlygoodthings.app.ui.screens.ChatScreen
import com.onlygoodthings.app.ui.screens.FeedScreen
import com.onlygoodthings.app.ui.screens.IdentityScreen
import com.onlygoodthings.app.ui.screens.ClaimHonorScreen
import com.onlygoodthings.app.ui.screens.InviteScreen
import com.onlygoodthings.app.ui.screens.LoginScreen
import com.onlygoodthings.app.ui.screens.OtpScreen
import com.onlygoodthings.app.ui.screens.RecoverScreen
import com.onlygoodthings.app.ui.screens.SignUpScreen
import com.onlygoodthings.app.ui.screens.MapScreen
import com.onlygoodthings.app.ui.screens.MessagesScreen
import com.onlygoodthings.app.ui.screens.NeighborProfileScreen
import com.onlygoodthings.app.ui.screens.MilestoneScreen
import com.onlygoodthings.app.ui.screens.NotificationsScreen
import com.onlygoodthings.app.ui.screens.OnboardingFeaturesScreen
import com.onlygoodthings.app.ui.screens.OnboardingScreen
import com.onlygoodthings.app.ui.screens.ParkHereScreen
import com.onlygoodthings.app.ui.screens.ParkingConfirmScreen
import com.onlygoodthings.app.ui.screens.ParkingScreen
import com.onlygoodthings.app.ui.screens.PetsMapScreen
import com.onlygoodthings.app.ui.screens.PermissionsScreen
import com.onlygoodthings.app.ui.screens.PostDetailScreen
import com.onlygoodthings.app.ui.screens.ProposeSkillScreen
import com.onlygoodthings.app.ui.screens.PublishActionScreen
import com.onlygoodthings.app.ui.screens.PublishAdoptionScreen
import com.onlygoodthings.app.ui.screens.RankingScreen
import com.onlygoodthings.app.ui.screens.ReportAnimalScreen
import com.onlygoodthings.app.ui.screens.SettingsScreen
import com.onlygoodthings.app.ui.screens.SkillsScreen
import com.onlygoodthings.app.ui.screens.SplashScreen
import com.onlygoodthings.app.ui.screens.SplashSponsorsScreen
import com.onlygoodthings.app.ui.screens.SponsorsScreen
import com.onlygoodthings.app.ui.screens.WalletScreen
import kotlinx.coroutines.launch

/** Entrada compartida: Android (`MainActivity`) e iOS (`MainViewController`). */
@Composable
fun App() {
    OgtPreviewStore {
        LushImpactTheme {
        val storePreview = remember { isStorePreviewLaunch() }
        var route by remember { mutableStateOf(if (storePreview) storePreviewRoute() else OgtRoute.Splash) }
        var otpBack by remember { mutableStateOf(OgtRoute.Login) }
        var chatBack by remember { mutableStateOf(OgtRoute.Skills) }
        var gpsReturn by remember { mutableStateOf(OgtRoute.Identity) }
        var reportBack by remember { mutableStateOf(OgtRoute.Animals) }
        var editAdoptionPostId by remember { mutableStateOf<String?>(null) }
        var animalPublishKind by remember {
            mutableStateOf(
                when (storePreviewScreenName()?.lowercase()) {
                    "adopt", "adopcion" -> ComposePostKind.ADOPT
                    else -> ComposePostKind.LOST
                },
            )
        }
        var gpsAwaiting by remember { mutableStateOf(false) }
        var selectedPostId by remember { mutableStateOf(OgtIds.PostTaller) }
        var selectedMatchId by remember { mutableStateOf(OgtIds.MatchCamila) }
        var selectedNeighborId by remember { mutableStateOf(OgtIds.ValeriaP) }
        var selectedSpotId by remember { mutableStateOf(OgtIds.SpotCorrientes) }
        var publishKind by remember {
            mutableStateOf(
                when (storePreviewScreenName()?.lowercase()) {
                    "homenaje" -> ComposePostKind.HOMENAJE
                    "ternura" -> ComposePostKind.TERNURA
                    else -> ComposePostKind.ACTION
                },
            )
        }
        val auth = LocalAuth.current
        if (storePreview) auth.markOnboardingDone()
        val session = LocalOgtSession.current
        val db = LocalOgtDb.current
        val copy = LocalOgtCopy.current
        val notify = rememberOgtNotify()
        val location = rememberOgtLocation(
            track = session.radarEnabled ||
                session.locationPermission == LocationPermissionState.GRANTED_ALWAYS ||
                route == OgtRoute.Parking ||
                route == OgtRoute.ParkingConfirm ||
                route == OgtRoute.ParkHere ||
                route == OgtRoute.Map ||
                route == OgtRoute.PetsMap ||
                route == OgtRoute.Gps ||
                route == OgtRoute.ReportAnimal,
        )
        val detector = remember { DriveParkingDetector() }
        var detectedManeuver by remember { mutableStateOf<ParkingManeuver?>(null) }
        LaunchedEffect(location.permission, location.servicesEnabled, location.fix, location.acquiring) {
            session.locationPermission = location.permission
            session.locationGranted = location.permission.isGranted()
            session.locationServicesEnabled = location.servicesEnabled
            session.locationAcquiring = location.acquiring
            session.locationAccuracy = location.fix?.accuracyMeters
            session.deviceLocation = location.fix?.toGeoPoint()
        }
        LaunchedEffect(route) {
            if (route == OgtRoute.Parking || route == OgtRoute.Map || route == OgtRoute.ParkHere || route == OgtRoute.PetsMap || route == OgtRoute.Gps || route == OgtRoute.ReportAnimal) {
                location.resync()
            }
        }
        LaunchedEffect(location.fix?.epochMs, location.fix?.latitude, location.fix?.longitude) {
            val fix = location.fix ?: return@LaunchedEffect
            val found = detector.onSample(
                MotionSample(fix.latitude, fix.longitude, fix.speedMps, fix.bearingDegrees, fix.epochMs),
            )
            if (found != null) {
                val existing = session.parkedCar
                val alreadyHere = existing != null &&
                    GeoMath.haversineMeters(existing.point(), GeoPoint(fix.latitude, fix.longitude)) < 40.0
                if (!alreadyHere) detectedManeuver = found
            }
        }
        LaunchedEffect(location.permission, location.servicesEnabled, gpsAwaiting, route) {
            if (!gpsAwaiting) return@LaunchedEffect
            if (location.permission.isGranted() && !location.servicesEnabled) {
                location.ensureServices()
            }
            if (location.permission.isGranted()) {
                location.refreshNow()
                gpsAwaiting = false
                if (route == OgtRoute.Gps) route = gpsReturn
            }
        }
        val scope = rememberCoroutineScope()
        LaunchedEffect(
            location.fix?.latitude,
            location.fix?.longitude,
            location.fix?.speedMps,
            location.fix?.epochMs,
            session.parkedCar,
            session.willLeaveParked,
            db.parkingEpoch,
        ) {
            val car = session.parkedCar ?: return@LaunchedEffect
            val fix = location.fix ?: return@LaunchedEffect
            val meters = GeoMath.haversineMeters(GeoPoint(fix.latitude, fix.longitude), car.point())
            if (meters > session.farthestFromParkedMeters) session.farthestFromParkedMeters = meters
            if (shouldAskYieldOnApproach(session.willLeaveParked, session.farthestFromParkedMeters, meters, session.yieldAsked)) {
                session.yieldAsked = true
                session.askYieldApproach = true
            }
            val yielding = db.parkingSpots.any {
                it.ownerUserId == session.me().id &&
                    it.status in listOf(ParkingStatus.AVAILABLE, ParkingStatus.CLAIMED)
            }
            val confirmAt = session.lastParkedConfirmEpochMs.takeIf { it > 0L } ?: car.parkedAtEpochMs
            when (
                decideParkedPresence(
                    ParkedPresenceTick(
                        metersFromCar = meters,
                        speedMps = fix.speedMps,
                        nowEpochMs = currentEpochMs(),
                        lastConfirmEpochMs = confirmAt,
                        wasNearCar = session.wasNearParkedCar,
                        sawVehicleDepart = session.sawVehicleDepart,
                        leaveAsked = session.leaveAsked,
                        hasActiveYield = yielding,
                        staleAsked = session.staleAsked,
                    ),
                )
            ) {
                ParkedPresenceAction.MARK_NEAR -> session.wasNearParkedCar = true
                ParkedPresenceAction.ASK_LEAVE -> {
                    session.sawVehicleDepart = true
                    session.leaveAsked = true
                    session.askLeaveWithoutYield = true
                }
                ParkedPresenceAction.FORGET_SILENT, ParkedPresenceAction.FORGET_STALE -> session.forgetParked()
                ParkedPresenceAction.ASK_STALE -> {
                    session.staleAsked = true
                    session.askStaleParked = true
                }
                ParkedPresenceAction.NONE -> Unit
            }
        }
        val showBar = route in OgtBottomTabs &&
            !session.askLocationScope &&
            detectedManeuver == null &&
            !session.askYieldApproach &&
            !session.askLeaveWithoutYield &&
            !session.askStaleParked
        fun applyHonorRoute(fallback: OgtRoute): OgtRoute {
            val fromContact = db.pendingHonorForUser(session.me())?.claimToken
            val token = session.pendingHonorToken ?: fromContact
            if (!token.isNullOrBlank()) {
                session.rememberHonorToken(token)
                return OgtRoute.ClaimHonor
            }
            return fallback
        }
        fun goHomeOrOnboard() {
            val home = if (auth.hasFinishedOnboarding()) OgtRoute.Feed else OgtRoute.Permissions
            route = applyHonorRoute(home)
        }
        val authGate = route == OgtRoute.Splash ||
            route == OgtRoute.Onboarding ||
            route == OgtRoute.OnboardingFeatures ||
            route == OgtRoute.Login ||
            route == OgtRoute.SignUp ||
            route == OgtRoute.Otp ||
            route == OgtRoute.Recover
        LaunchedEffect(OgtIncomingLinks.latest) {
            val token = OgtIncomingLinks.latest ?: return@LaunchedEffect
            session.rememberHonorToken(token)
            OgtIncomingLinks.clear()
            if (!authGate && route != OgtRoute.ClaimHonor) {
                route = OgtRoute.ClaimHonor
            }
        }
        LaunchedEffect(Unit) {
            if (session.pendingHonorToken.isNullOrBlank() && OgtIncomingLinks.latest == null) {
                readHonorClipboard()?.let { OgtIncomingLinks.offer(honorAppLink(it)) }
            }
            if (session.takeHonorReferrerSlot()) {
                startHonorInstallReferrer { token ->
                    if (session.pendingHonorToken.isNullOrBlank()) {
                        OgtIncomingLinks.offer(honorAppLink(token))
                    }
                }
            }
        }
        LaunchedEffect(OgtIncomingLinks.latestPostId) {
            val postId = OgtIncomingLinks.latestPostId ?: return@LaunchedEffect
            if (db.post(postId) != null) {
                selectedPostId = postId
                OgtIncomingLinks.clearPost()
                if (!authGate) route = OgtRoute.PostDetail
            }
        }
        LaunchedEffect(notify.token, auth.session.firebaseJwt) {
            val token = notify.token ?: return@LaunchedEffect
            if (auth.session.firebaseJwt.isNullOrBlank()) return@LaunchedEffect
            auth.notices.registerToken(token, notify.platform)
        }
        LaunchedEffect(route) {
            if (route == OgtRoute.Feed || route == OgtRoute.Permissions || route == OgtRoute.Notifications) {
                notify.requestPermission()
            }
        }
        val stackBack: (() -> Unit)? = when {
            detectedManeuver != null -> ({ detectedManeuver = null })
            session.askStaleParked -> ({ session.askStaleParked = false })
            session.askLeaveWithoutYield -> ({ session.askLeaveWithoutYield = false })
            session.askYieldApproach -> ({
                session.askYieldApproach = false
                session.willLeaveParked = false
            })
            session.askLocationScope && route != OgtRoute.Gps -> ({ session.askLocationScope = false })
            else -> when (route) {
                OgtRoute.Otp -> ({ route = otpBack })
                OgtRoute.Recover, OgtRoute.SignUp -> ({ route = OgtRoute.Login })
                OgtRoute.NeighborProfile, OgtRoute.PostDetail, OgtRoute.Publish,
                OgtRoute.Notifications, OgtRoute.Invite, OgtRoute.ClaimHonor, OgtRoute.Messages,
                -> ({ route = OgtRoute.Feed })
                OgtRoute.ParkingConfirm, OgtRoute.ParkHere, OgtRoute.Map -> ({ route = OgtRoute.Parking })
                OgtRoute.PetsMap -> ({ route = OgtRoute.Animals })
                OgtRoute.ReportAnimal -> ({ route = reportBack })
                OgtRoute.AdoptApply -> ({ route = OgtRoute.PostDetail })
                OgtRoute.ProposeSkill -> ({ route = OgtRoute.Skills })
                OgtRoute.Chat -> ({ route = chatBack })
                OgtRoute.Wallet, OgtRoute.Ranking, OgtRoute.Sponsors -> ({ route = OgtRoute.Settings })
                OgtRoute.Milestone -> ({ route = OgtRoute.Feed })
                OgtRoute.Gps -> ({
                    gpsAwaiting = false
                    session.askLocationScope = false
                    route = gpsReturn
                })
                else -> null
            }
        }
        OgtBackHandler(enabled = stackBack != null) { stackBack?.invoke() }
        CompositionLocalProvider(LocalOgtLocation provides location) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Box(Modifier.fillMaxSize().ogtDismissImeOnScroll()) {
                when (route) {
                    OgtRoute.Splash -> SplashScreen {
                        scope.launch {
                            route = when (auth.restore()) {
                                AuthRestore.LoggedOut -> OgtRoute.Onboarding
                                AuthRestore.NeedsBiometric -> OgtRoute.Login
                                AuthRestore.FirstRun -> OgtRoute.Permissions
                                AuthRestore.Home -> applyHonorRoute(OgtRoute.Feed)
                            }
                            auth.hydratePublishedAnimals(db)
                            session.persistPublishedAnimals()
                        }
                    }
                    OgtRoute.AnimatedSvg -> AnimatedSvgScreen()
                    OgtRoute.SplashSponsors -> SplashSponsorsScreen { route = OgtRoute.Onboarding }
                    OgtRoute.OnboardingFeatures -> OnboardingScreen(
                        onDone = { route = OgtRoute.SignUp },
                        onLogin = { route = OgtRoute.Login },
                    )
                    OgtRoute.Onboarding -> OnboardingScreen(
                        onDone = { route = OgtRoute.SignUp },
                        onLogin = { route = OgtRoute.Login },
                    )
                    OgtRoute.SignUp -> SignUpScreen(
                        onOtp = {
                            otpBack = OgtRoute.SignUp
                            route = OgtRoute.Otp
                        },
                        onLogin = { route = OgtRoute.Login },
                        onRegistered = { goHomeOrOnboard() },
                    )
                    OgtRoute.Login -> LoginScreen(
                        onEnter = { goHomeOrOnboard() },
                        onSignUp = { route = OgtRoute.SignUp },
                        onRecover = { route = OgtRoute.Recover },
                        onOtp = {
                            otpBack = OgtRoute.Login
                            route = OgtRoute.Otp
                        },
                    )
                    OgtRoute.Otp -> OtpScreen(
                        onConfirm = { goHomeOrOnboard() },
                        onBack = { route = otpBack },
                    )
                    OgtRoute.Recover -> RecoverScreen(
                        onSent = { route = OgtRoute.Login },
                        onLogin = { route = OgtRoute.Login },
                        onOtp = {
                            otpBack = OgtRoute.Recover
                            route = OgtRoute.Otp
                        },
                    )
                    OgtRoute.Permissions -> PermissionsScreen(
                        onContinue = {
                            gpsReturn = OgtRoute.Identity
                            route = OgtRoute.Gps
                        },
                        onRequestNotify = notify.requestPermission,
                    )
                    OgtRoute.Gps -> LocationScopeDialog(
                        onConfirm = { scope ->
                            gpsAwaiting = true
                            session.askLocationScope = false
                            location.requestPermission(scope)
                            location.ensureServices()
                            location.refreshNow()
                            if (location.permission.isGranted()) {
                                gpsAwaiting = false
                                route = gpsReturn
                            }
                        },
                        onSkip = {
                            gpsAwaiting = false
                            session.askLocationScope = false
                            session.locationGranted = false
                            session.deviceLocation = null
                            route = gpsReturn
                        },
                    )
                    OgtRoute.Identity -> IdentityScreen {
                        auth.markOnboardingDone()
                        route = OgtRoute.Feed
                    }
                    OgtRoute.Feed, OgtRoute.PostDetail, OgtRoute.NeighborProfile -> {
                        // El río sigue compuesto bajo la ficha: atrás vuelve a la misma tarjeta.
                        FeedScreen(
                            onOpenPost = { id ->
                                selectedPostId = id
                                route = OgtRoute.PostDetail
                            },
                            onNotifications = { route = OgtRoute.Notifications },
                            onProfile = { route = OgtRoute.Settings },
                            onMessages = { route = OgtRoute.Messages },
                            onInvite = { route = OgtRoute.Invite },
                            onOpenNeighbor = { userId ->
                                selectedNeighborId = userId
                                route = OgtRoute.NeighborProfile
                            },
                            onPetsMap = { route = OgtRoute.PetsMap },
                            onAdopt = { route = OgtRoute.Animals },
                            onEditAdoption = { id ->
                                editAdoptionPostId = id
                                selectedPostId = id
                                animalPublishKind = ComposePostKind.ADOPT
                                reportBack = OgtRoute.Feed
                                route = OgtRoute.ReportAnimal
                            },
                        )
                        if (route == OgtRoute.NeighborProfile) {
                            NeighborProfileScreen(
                                userId = selectedNeighborId,
                                onBack = { route = OgtRoute.Feed },
                                onOpenPost = { id ->
                                    selectedPostId = id
                                    route = OgtRoute.PostDetail
                                },
                            )
                        }
                        if (route == OgtRoute.PostDetail) {
                            PostDetailScreen(
                                postId = selectedPostId,
                                onBack = { route = OgtRoute.Feed },
                                onPetsMap = { route = OgtRoute.PetsMap },
                                onAdopt = { route = OgtRoute.AdoptApply },
                                onEdit = {
                                    editAdoptionPostId = selectedPostId
                                    animalPublishKind = ComposePostKind.ADOPT
                                    reportBack = OgtRoute.PostDetail
                                    route = OgtRoute.ReportAnimal
                                },
                                onInvite = { route = OgtRoute.Invite },
                                onOpenLostChat = { matchId ->
                                    selectedMatchId = matchId
                                    chatBack = OgtRoute.PostDetail
                                    route = OgtRoute.Chat
                                },
                            )
                        }
                    }
                    OgtRoute.Publish -> PublishActionScreen(
                        kind = publishKind,
                        onDone = { route = OgtRoute.Feed },
                        onBack = { route = OgtRoute.Feed },
                    )
                    OgtRoute.Parking -> ParkingScreen(
                        onConfirm = { id ->
                            selectedSpotId = id
                            route = OgtRoute.ParkingConfirm
                        },
                        onMap = { route = OgtRoute.Map },
                        onParkHere = { route = OgtRoute.ParkHere },
                    )
                    OgtRoute.ParkingConfirm -> ParkingConfirmScreen(selectedSpotId) { route = OgtRoute.Parking }
                    OgtRoute.ParkHere -> ParkHereScreen(
                        onDone = { route = OgtRoute.Parking },
                        onBack = { route = OgtRoute.Parking },
                    )
                    OgtRoute.Map -> MapScreen(
                        onBack = { route = OgtRoute.Parking },
                        onClaimed = { route = OgtRoute.Parking },
                    )
                    OgtRoute.PetsMap -> PetsMapScreen(
                        onBack = { route = OgtRoute.Animals },
                        onReport = {
                            animalPublishKind = ComposePostKind.LOST
                            reportBack = OgtRoute.Animals
                            route = OgtRoute.ReportAnimal
                        },
                    )
                    OgtRoute.Animals -> AnimalsScreen(
                        onReportLost = {
                            animalPublishKind = ComposePostKind.LOST
                            reportBack = OgtRoute.Animals
                            route = OgtRoute.ReportAnimal
                        },
                        onReportAdopt = {
                            editAdoptionPostId = null
                            animalPublishKind = ComposePostKind.ADOPT
                            reportBack = OgtRoute.Animals
                            route = OgtRoute.ReportAnimal
                        },
                        onPetsMap = { route = OgtRoute.PetsMap },
                        onOpenAdoption = { id ->
                            selectedPostId = id
                            route = OgtRoute.PostDetail
                        },
                        onEditAdoption = { id ->
                            editAdoptionPostId = id
                            selectedPostId = id
                            animalPublishKind = ComposePostKind.ADOPT
                            reportBack = OgtRoute.Animals
                            route = OgtRoute.ReportAnimal
                        },
                    )
                    OgtRoute.ReportAnimal -> if (animalPublishKind == ComposePostKind.ADOPT) {
                        PublishAdoptionScreen(
                            editPostId = editAdoptionPostId,
                            onBack = {
                                editAdoptionPostId = null
                                route = reportBack
                            },
                            onPublished = {
                                editAdoptionPostId = null
                                route = reportBack
                            },
                        )
                    } else {
                        ReportAnimalScreen { route = reportBack }
                    }
                    OgtRoute.AdoptApply -> AdoptApplicationScreen(
                        postId = selectedPostId,
                        onBack = { route = OgtRoute.PostDetail },
                        onSent = { matchId ->
                            selectedMatchId = matchId
                            chatBack = OgtRoute.Messages
                            route = OgtRoute.Chat
                        },
                    )
                    OgtRoute.Skills -> SkillsScreen(
                        onPropose = { route = OgtRoute.ProposeSkill },
                        onChat = { matchId ->
                            selectedMatchId = matchId
                            chatBack = OgtRoute.Skills
                            route = OgtRoute.Chat
                        },
                    )
                    OgtRoute.ProposeSkill -> ProposeSkillScreen { route = OgtRoute.Skills }
                    OgtRoute.Messages -> MessagesScreen(
                        onOpenChat = { matchId ->
                            selectedMatchId = matchId
                            chatBack = OgtRoute.Messages
                            route = OgtRoute.Chat
                        },
                        onBack = { route = OgtRoute.Feed },
                    )
                    OgtRoute.Chat -> ChatScreen(selectedMatchId) { route = chatBack }
                    OgtRoute.Wallet -> WalletScreen { route = OgtRoute.Settings }
                    OgtRoute.Ranking -> RankingScreen { route = OgtRoute.Settings }
                    OgtRoute.Notifications -> NotificationsScreen(
                        onBack = { route = OgtRoute.Feed },
                        onOpenPost = { postId ->
                            selectedPostId = postId
                            route = OgtRoute.PostDetail
                        },
                    )
                    OgtRoute.Invite -> InviteScreen { route = OgtRoute.Feed }
                    OgtRoute.ClaimHonor -> ClaimHonorScreen(
                        onDone = {
                            session.rememberHonorToken(null)
                            route = if (auth.hasFinishedOnboarding()) OgtRoute.Feed else OgtRoute.Permissions
                        },
                        onBack = {
                            session.rememberHonorToken(null)
                            route = if (auth.hasFinishedOnboarding()) OgtRoute.Feed else OgtRoute.Permissions
                        },
                    )
                    OgtRoute.Settings -> SettingsScreen(
                        onLogout = {
                            scope.launch {
                                auth.signOut()
                                route = OgtRoute.Login
                            }
                        },
                    ) { key ->
                        route = when (key) {
                            "wallet" -> OgtRoute.Wallet
                            "ranking" -> OgtRoute.Ranking
                            "invite" -> OgtRoute.Invite
                            "messages" -> OgtRoute.Messages
                            "sponsors" -> OgtRoute.Sponsors
                            "milestone" -> OgtRoute.Milestone
                            "splashSponsors" -> OgtRoute.SplashSponsors
                            "gps" -> {
                                gpsReturn = OgtRoute.Settings
                                OgtRoute.Gps
                            }
                            "permissions" -> OgtRoute.Permissions
                            "animatedSvg" -> OgtRoute.AnimatedSvg
                            else -> OgtRoute.Settings
                        }
                    }
                    OgtRoute.Sponsors -> SponsorsScreen { route = OgtRoute.Settings }
                    OgtRoute.Milestone -> MilestoneScreen { route = OgtRoute.Feed }
                }
                if (session.askLocationScope && route != OgtRoute.Gps) {
                    LocationScopeDialog(
                        onConfirm = { scope ->
                            session.askLocationScope = false
                            gpsAwaiting = true
                            location.requestPermission(scope)
                            location.ensureServices()
                            location.refreshNow()
                        },
                        onSkip = { session.askLocationScope = false },
                    )
                }
                if (session.askYieldApproach && detectedManeuver == null) {
                    YieldApproachDialog(
                        onYield = {
                            val car = session.parkedCar
                            val fix = location.fix?.toGeoPoint() ?: car?.point()
                            session.askYieldApproach = false
                            session.willLeaveParked = false
                            if (fix != null) {
                                scope.launch {
                                    runCatching {
                                        com.onlygoodthings.shared.data.local.LocalParkingRepository(
                                            db,
                                            session.me().id,
                                        ).publishVacancy(
                                            location = car?.point() ?: fix,
                                            ttlMinutes = ParkingRules.DEFAULT_TTL_MINUTES,
                                            notes = "Cedo mi lugar al irme",
                                            vehicleLabel = session.parkedCar?.vehicle?.label() ?: session.vehicle.takeIf { it.isReady() }?.label(),
                                            ownerLocation = fix,
                                        )
                                        session.showYieldNotifying = true
                                    }
                                    session.forgetParked()
                                }
                            }
                            route = OgtRoute.Parking
                        },
                        onDismiss = {
                            session.askYieldApproach = false
                            session.willLeaveParked = false
                        },
                    )
                }
                if (session.askLeaveWithoutYield && !session.askYieldApproach && detectedManeuver == null) {
                    ParkedPromptDialog(
                        pill = copy.yourCar,
                        title = copy.leaveTitle,
                        body = copy.leaveBody,
                        primaryLabel = copy.leaveYes,
                        secondaryLabel = copy.leaveNo,
                        onPrimary = {
                            val car = session.parkedCar
                            val fix = location.fix?.toGeoPoint() ?: car?.point()
                            session.askLeaveWithoutYield = false
                            if (car != null && fix != null) {
                                scope.launch {
                                    runCatching {
                                        com.onlygoodthings.shared.data.local.LocalParkingRepository(
                                            db,
                                            session.me().id,
                                        ).publishVacancy(
                                            location = car.point(),
                                            ttlMinutes = ParkingRules.DEFAULT_TTL_MINUTES,
                                            notes = copy.leavingNotes(session.me().barrio),
                                            vehicleLabel = car.vehicle?.label() ?: session.vehicle.takeIf { it.isReady() }?.label(),
                                            ownerLocation = fix,
                                            leftoverNow = true,
                                        )
                                        session.showYieldNotifying = true
                                    }
                                    session.forgetParked()
                                }
                            } else {
                                session.forgetParked()
                            }
                            route = OgtRoute.Parking
                        },
                        onSecondary = {
                            session.forgetParked()
                        },
                    )
                }
                if (session.askStaleParked && !session.askLeaveWithoutYield && !session.askYieldApproach && detectedManeuver == null) {
                    ParkedPromptDialog(
                        pill = copy.yourCar,
                        title = copy.staleTitle,
                        body = copy.staleBody,
                        primaryLabel = copy.staleYes,
                        secondaryLabel = copy.staleNo,
                        onPrimary = { session.confirmStillParked() },
                        onSecondary = { session.forgetParked() },
                    )
                }
                detectedManeuver?.let { maneuver ->
                    ParkingManeuverDialog(
                        maneuver = maneuver,
                        onMemorize = {
                            detectedManeuver = null
                            route = OgtRoute.ParkHere
                        },
                        onYield = {
                            val fix = location.fix
                            if (fix != null) {
                                scope.launch {
                                    runCatching {
                                        com.onlygoodthings.shared.data.local.LocalParkingRepository(
                                            db,
                                            session.me().id,
                                        ).publishVacancy(
                                            location = fix.toGeoPoint(),
                                            ttlMinutes = ParkingRules.DEFAULT_TTL_MINUTES,
                                            notes = "Cedo mi lugar al irme",
                                            vehicleLabel = session.vehicle.takeIf { it.isReady() }?.label(),
                                            ownerLocation = fix.toGeoPoint(),
                                        )
                                        session.showYieldNotifying = true
                                    }
                                    session.forgetParked()
                                }
                            }
                            detectedManeuver = null
                            route = OgtRoute.Parking
                        },
                        onDismiss = { detectedManeuver = null },
                    )
                }
                if (showBar) {
                    OgtBottomBar(
                        current = route,
                        onSelect = { route = it },
                            onCompose = { kind ->
                            when (kind) {
                                ComposePostKind.ACTION, ComposePostKind.GATHERING,
                                ComposePostKind.TERNURA, ComposePostKind.HOMENAJE -> {
                                    publishKind = kind
                                    route = OgtRoute.Publish
                                }
                                ComposePostKind.LOST, ComposePostKind.ADOPT -> {
                                    editAdoptionPostId = null
                                    animalPublishKind = kind
                                    reportBack = OgtRoute.Feed
                                    route = OgtRoute.ReportAnimal
                                }
                                ComposePostKind.SKILL -> route = OgtRoute.ProposeSkill
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        }
        }
    }
}
