package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.platform.sharePlainText
import com.onlygoodthings.app.platform.rememberOgtMediaPicker
import com.onlygoodthings.app.ui.components.OgtBottomSheet
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.publish_action_add
import org.jetbrains.compose.resources.painterResource
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.LocationPermissionState
import com.onlygoodthings.app.map.covers
import com.onlygoodthings.app.map.isGranted
import com.onlygoodthings.app.notify.rememberOgtNotify
import com.onlygoodthings.app.platform.rememberHonorInviteActions
import com.onlygoodthings.app.platform.rememberOgtCameraAccess
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.CarSilhouette
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.PrefNav
import com.onlygoodthings.app.ui.components.PrefRadio
import com.onlygoodthings.app.ui.components.PrefRevealDelete
import com.onlygoodthings.app.ui.components.PrefSwitch
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.LocationScope
import kotlinx.coroutines.launch

/**
 * Ajustes al modo sistema: grupos, switch, fila de idioma.
 * Permisos del dispositivo viven acá. El garage se usa al ceder un lugar.
 */
@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    onBack: () -> Unit = {},
    showChrome: Boolean = true,
    onOpen: (String) -> Unit,
) {
    val session = LocalOgtSession.current
    val auth = LocalAuth.current
    val db = LocalOgtDb.current
    val scope = rememberCoroutineScope()
    var confirmOpen by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    val copy = LocalOgtCopy.current
    val me = session.me()
    val gps = LocalOgtLocation.current
    val notify = rememberOgtNotify()
    val contacts = rememberHonorInviteActions()
    val camera = rememberOgtCameraAccess()
    val gpsDetail = when {
        !session.gpsEnabled -> null
        gps.permission == LocationPermissionState.DENIED_FOREVER -> "Bloqueado en el sistema"
        !gps.permission.covers(session.locationScope) &&
            session.locationScope == LocationScope.ALWAYS -> "Falta el permiso en segundo plano"
        !gps.permission.isGranted() -> "Sin permiso de ubicación"
        !gps.servicesEnabled -> "Apagado en el teléfono"
        gps.acquiring -> "Buscando señal"
        session.here() != null -> session.locationAccuracy?.let { "Señal ±${it.toInt()} m" } ?: "Con señal"
        else -> "Sin señal"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp),
    ) {
        if (showChrome) OgtTopBar(title = "Ajustes", onBack = onBack)
        ScreenColumn {
            ProfilePhotoEditor()
            Text(
                me.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = OgtColors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!me.email.isNullOrBlank()) {
                Text(
                    me.email.orEmpty(),
                    color = OgtColors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            Text(
                "${me.levelLabel} · ${me.communityPoints} pts",
                color = OgtColors.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )

            PrefCategory(copy.languageTitle)
            PrefGroup {
                PrefNav(
                    title = copy.languageTitle,
                    value = if (session.language == OgtLang.ES) copy.languageEs else copy.languageEn,
                ) { onOpen("language") }
            }

            PrefCategory(copy.yourCars, copy.vehicleWhy)
            PrefGroup {
                session.vehicles.forEachIndexed { index, car ->
                    if (index > 0) PrefDivider()
                    PrefRevealDelete(onDelete = { session.removeVehicle(car.id) }) {
                        PrefLine(
                            title = car.make.ifBlank { copy.vehicleTitle },
                            body = listOf(car.color, car.plate).filter { it.isNotBlank() }.joinToString(" · "),
                            trailing = if (session.selectedVehicleId == car.id) copy.carInUse else null,
                            leading = { CarSilhouette(car.colorHex, width = 56.dp) },
                        ) { onOpen("car:${car.id}") }
                    }
                }
                if (session.vehicles.isNotEmpty()) PrefDivider()
                PrefNav(copy.addAnotherCar) { onOpen("car") }
            }

            PrefCategory("Permisos", "Para ceder un lugar, avisar de una mascota o invitar. Los podés apagar cuando quieras.")
            PrefGroup {
                PrefSwitch(
                    title = "GPS",
                    body = gpsDetail,
                    checked = session.gpsEnabled,
                ) { on ->
                    session.applyGpsEnabled(on)
                    if (!on) return@PrefSwitch
                    gps.ensureScope(session.locationScope)
                }
                AnimatedVisibility(
                    visible = session.gpsEnabled,
                    enter = fadeIn(OgtMotion.fade) + expandVertically(OgtMotion.size),
                    exit = fadeOut(OgtMotion.fade) + shrinkVertically(OgtMotion.size),
                ) {
                    Column {
                        PrefDivider()
                        PrefRadio(
                            title = "Mientras uses la app",
                            body = "Sincroniza cesiones y distancias con Parking o el mapa abiertos.",
                            selected = session.locationScope == LocationScope.WHILE_USING,
                        ) {
                            session.applyLocationScope(LocationScope.WHILE_USING)
                            gps.ensureScope(LocationScope.WHILE_USING)
                        }
                        PrefDivider()
                        PrefRadio(
                            title = "Siempre",
                            body = "Sigue midiendo aunque cambies de app. No guardamos el historial.",
                            selected = session.locationScope == LocationScope.ALWAYS,
                        ) {
                            session.applyLocationScope(LocationScope.ALWAYS)
                            gps.ensureScope(LocationScope.ALWAYS)
                        }
                    }
                }
                PrefDivider()
                PrefLine(
                    title = "Notificaciones",
                    body = "Plazas, matches y menciones. De 22 a 7 se silencia solo.",
                    trailing = if (notify.permissionGranted) "Activo" else "Pedir",
                ) { if (!notify.permissionGranted) notify.retryPermission() }
                PrefDivider()
                PrefLine(
                    title = "Contactos",
                    body = "Para elegir a quién invitar. No subimos tu agenda.",
                    trailing = if (contacts.contactsAllowed) "Permitido" else "Pedir",
                ) { if (!contacts.contactsAllowed) contacts.requestContacts() }
                PrefDivider()
                PrefLine(
                    title = "Cámara y fotos",
                    body = "Para publicar un recuerdo o un reporte. Se limpian los EXIF sensibles.",
                    trailing = if (camera.granted) "Permitido" else "Pedir",
                ) { if (!camera.granted) camera.request() }
                PrefDivider()
                PrefSwitch(
                    title = "Radar de estacionamiento",
                    body = "Detecta plazas solidarias cerca tuyo",
                    checked = session.radarEnabled,
                ) { session.radarEnabled = it }
                PrefDivider()
                PrefSwitch(
                    title = "Ahorro de huella",
                    body = "Agrupa trayectos y avisos eco locales",
                    checked = session.carbonSaveMode,
                ) { session.carbonSaveMode = it }
            }

            PrefCategory("Privacidad")
            PrefGroup {
                PrefSwitch(
                    title = "Ubicación exacta en matches",
                    body = "Si está apagado, solo se ve la distancia relativa",
                    checked = session.showExactMatchLocation,
                ) { session.showExactMatchLocation = it }
                PrefDivider()
                PrefSwitch(
                    title = "Perfil visible sin cuenta",
                    body = "Permite validar donaciones comunitarias",
                    checked = session.publicProfileVisible,
                ) { session.publicProfileVisible = it }
                PrefDivider()
                PrefNav("Descargar mis datos", body = "Historial de impacto") {
                    sharePlainText(impactExport(me, db))
                }
            }

            PrefCategory("Notificaciones")
            PrefGroup {
                PrefSwitch(
                    title = "Animales en riesgo",
                    body = "Alertas push inmediatas",
                    checked = session.animalAlertPush,
                ) { session.animalAlertPush = it }
                PrefDivider()
                PrefSwitch(
                    title = "Intercambio de ayuda",
                    body = "Avisos de una mano por otra",
                    checked = session.skillAlertPush,
                ) { session.skillAlertPush = it }
                PrefDivider()
                PrefSwitch(
                    title = "Sonidos de radar",
                    body = "Aviso al ceder o pedir un lugar",
                    checked = session.parkingRadarSounds,
                ) { session.parkingRadarSounds = it }
            }

            PrefCategory("Comunidad")
            PrefGroup {
                PrefNav("Reglas de la comunidad", body = "Qué se publica y qué no") { onOpen("rules") }
            }

            PrefCategory("Cuenta")
            PrefGroup {
                listOf(
                    "Invitar amigos y contactos" to "invite",
                    "Billetera de puntos" to "wallet",
                    "Ranking de la comunidad" to "ranking",
                    "Mensajes" to "messages",
                    "Sponsors RSE" to "sponsors",
                    "Celebrar hito" to "milestone",
                    "Splash sponsors" to "splashSponsors",
                    "Logo animado" to "animatedSvg",
                ).forEachIndexed { index, (label, key) ->
                    if (index > 0) PrefDivider()
                    PrefNav(label) { onOpen(key) }
                }
            }
            Text(
                "Cerrar sesión",
                color = OgtColors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLogout)
                    .padding(vertical = 16.dp),
            )
            Text(
                "Borrar cuenta",
                color = OgtColors.error,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        confirmOpen = !confirmOpen
                        if (confirmOpen) auth.error = null
                    }
                    .padding(vertical = 8.dp),
            )
            AnimatedVisibility(
                visible = confirmOpen,
                enter = fadeIn(OgtMotion.fade) + expandVertically(OgtMotion.size),
                exit = fadeOut(OgtMotion.fade) + shrinkVertically(OgtMotion.size),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OgtCaption(
                        "Se borra esta cuenta y lo que publicaste con ella: avisos, comentarios y el resto del contenido cuyo autor sos vos. No queda visible ni a nombre de otra persona. No se borra la copia que otro vecino ya tenga en su chat o en una exportación, un resguardo técnico corto, ni lo que haya que conservar por un reclamo o un pedido de autoridad. Esos restos no se reutilizan. El ingreso de Firebase sigue existiendo hasta que lo borres en Google.",
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it.uppercase().take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("BAJA", color = OgtColors.muted) },
                        singleLine = true,
                        colors = ogtOutlinedFieldColors(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    auth.error?.let {
                        Text(it, color = OgtColors.error, fontSize = 13.sp)
                    }
                    OgtPrimaryButton(
                        text = if (auth.busy) "Borrando…" else "Borrar mi cuenta",
                        enabled = confirmText == "BAJA" && !auth.busy,
                    ) {
                        scope.launch {
                            if (auth.deleteAccount()) onLogout()
                        }
                    }
                }
            }
            OgtCaption("Only Good Things (OGT) v2.8.4")
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun impactExport(me: LocalUser, db: OgtLocalDatabase): String = buildString {
    appendLine("OnlyGoodThings")
    appendLine(me.levelLabel)
    appendLine("${me.communityPoints} pts")
    if (me.barrio.isNotBlank()) appendLine(me.barrio)
    db.karma.filter { it.userId == me.id }.forEach { entry ->
        val sign = if (entry.delta >= 0) "+" else ""
        appendLine("$sign${entry.delta} · ${entry.title}")
    }
}

/** Foto centrada. El origen se elige en un sheet, después de cerrarlo. */
@Composable
private fun ProfilePhotoEditor() {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPick by remember { mutableStateOf<(() -> Unit)?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sheet, pendingPick) {
        val action = pendingPick ?: return@LaunchedEffect
        if (sheet) return@LaunchedEffect
        pendingPick = null
        action()
    }
    val picker = rememberOgtMediaPicker(1) { picked ->
        if (picked.kind != MediaKind.IMAGE) {
            photoError = "Elegí una foto"
            return@rememberOgtMediaPicker
        }
        photoError = null
        saved = false
        if (saving) return@rememberOgtMediaPicker
        saving = true
        scope.launch {
            try {
                auth.saveMyProfilePhoto(picked.path)
                saved = true
            } catch (err: Exception) {
                photoError = err.message ?: "No se pudo guardar la imagen en el servidor"
            } finally {
                saving = false
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) OgtMotion.pressScale else 1f,
            animationSpec = OgtMotion.press,
            label = "profile-photo-press",
        )
        Box(
            Modifier
                .size(112.dp)
                .scale(scale)
                .clickable(enabled = !saving, interactionSource = press, indication = null) { sheet = true },
            contentAlignment = Alignment.Center,
        ) {
            OgtSessionAvatar(
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .border(1.dp, OgtColors.hairline, CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(OgtColors.canvas)
                    .border(1.dp, OgtColors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(Res.drawable.publish_action_add),
                    contentDescription = "Cambiar foto",
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (saving || saved) {
            Text(
                if (saving) "Guardando foto en tu perfil…" else "Foto guardada y sincronizada",
                color = if (saving) OgtColors.muted else OgtColors.primary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!photoError.isNullOrBlank()) {
            Text(
                photoError.orEmpty(),
                color = OgtColors.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (sheet) {
        OgtBottomSheet(onDismiss = {
            pendingPick = afterClose
            afterClose = null
            sheet = false
        }, maxHeight = 280.dp) { closer ->
            ProfilePhotoSheetRow("Tomar foto") {
                afterClose = { picker.takeCamera() }
                closer.close()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OgtColors.hairline))
            ProfilePhotoSheetRow("Biblioteca de fotos") {
                afterClose = { picker.pickPhoto() }
                closer.close()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OgtColors.hairline))
            ProfilePhotoSheetRow("Cancelar", muted = true) { closer.close() }
        }
    }
}

@Composable
private fun ProfilePhotoSheetRow(label: String, muted: Boolean = false, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "profile-sheet-row",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = if (muted) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 16.sp,
            color = if (muted) OgtColors.muted else OgtColors.ink,
        )
    }
}
