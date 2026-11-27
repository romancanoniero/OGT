package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.map.*
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Optional point callback keeps the confirmed place coordinates alongside its label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicationPlaceField(
    value:String,
    onValueChange:(String)->Unit,
    label:String="Lugar",
    enabled:Boolean=true,
    locateInitially:Boolean=false,
    onPointChange:(com.onlygoodthings.shared.domain.GeoPoint?)->Unit={},
) {
    val preview=LocalInspectionMode.current
    val focusManager=LocalFocusManager.current
    val auth=LocalAuth.current;val session=LocalOgtSession.current
    val gps=if(preview)remember { LocationUiState() } else LocalOgtLocation.current
    var search by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<com.onlygoodthings.shared.domain.GeoHit>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var locationRequest by remember { mutableIntStateOf(0) }
    var locatePending by remember { mutableStateOf(locateInitially && value.isBlank()) }
    var locating by remember { mutableStateOf(false) }
    var prompted by remember(locationRequest) { mutableStateOf<LocationRequirement?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    var menuRequested by remember { mutableStateOf(false) }

    LaunchedEffect(search,enabled) {
        hits=emptyList()
        if(!enabled || preview || search.trim().length<3)return@LaunchedEffect
        val expected=revision
        delay(450)
        status="Buscando lugares…"
        try {
            val result=auth.feed.publicationPlaces(search.trim())
            if(revision==expected) {
                hits=result
                status=if(result.isEmpty())"No encontramos lugares con ese texto." else null
            }
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { if(revision==expected)status="No pudimos buscar lugares. Reintentá." }
    }

    LaunchedEffect(locatePending,locationRequest,enabled,session.gpsEnabled,gps.permission,gps.servicesEnabled) {
        if(!locatePending || !enabled || preview)return@LaunchedEffect
        val requirement=locationRequirement(session.gpsEnabled,gps.permission,gps.servicesEnabled,false,false)
        if(requirement !in listOf(LocationRequirement.NO_FIX,LocationRequirement.ACQUIRING,LocationRequirement.READY)) {
            status=when(requirement) {
                LocationRequirement.PERMISSION_BLOCKED -> "El permiso de ubicación está desactivado. Podés abrir Ajustes o escribir el lugar."
                LocationRequirement.UNSUPPORTED -> "Este dispositivo no ofrece ubicación. Escribí el lugar."
                LocationRequirement.SYSTEM_DISABLED -> "Activá la ubicación del dispositivo o escribí el lugar."
                else -> "Permití usar la ubicación o escribí el lugar libremente."
            }
            // A blocked permission never sends the user to Settings without a separate explicit tap.
            if(prompted!=requirement && requirement !in listOf(LocationRequirement.PERMISSION_BLOCKED,LocationRequirement.UNSUPPORTED)) {
                prompted=requirement
                ensureLocationAccess(session.gpsEnabled,gps) { session.askLocationScope=true }
            }
            return@LaunchedEffect
        }
        val expected=revision
        locating=true;status="Buscando tu ubicación…"
        try {
            gps.refreshNow()
            val point=withTimeoutOrNull(10_000) {
                snapshotFlow { gps.fix?.takeIf { currentEpochMs()-it.epochMs in 0L..120_000L }?.toGeoPoint() }.filterNotNull().first()
            }
            if(revision!=expected)return@LaunchedEffect
            if(point==null) { status="No recibimos una ubicación actual. Reintentá o escribí el lugar.";return@LaunchedEffect }
            val hit=try { auth.feed.publicationPlace(point) }
                catch(cancel:CancellationException) { throw cancel }
                catch(_:Exception) { null }
            if(revision!=expected)return@LaunchedEffect
            if(hit==null)status="No pudimos identificar el lugar. Reintentá o escribilo libremente."
            else { search="";hits=emptyList();onValueChange(hit.label);onPointChange(com.onlygoodthings.shared.domain.GeoPoint(hit.latitude,hit.longitude));status="Lugar de tu ubicación actual. Podés cambiarlo antes de publicar." }
        } finally { locating=false;if(revision==expected)locatePending=false }
    }

    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        val expanded=enabled && fieldFocused && menuRequested && hits.isNotEmpty()
        ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={menuRequested=it}) {
            OgtOutlinedTextField(value,{ text ->
                revision++;locatePending=false;locating=false;hits=emptyList();status=null;menuRequested=true
                val next=text.take(200);search=next;onValueChange(next);onPointChange(null)
            },label={Text(label)},placeholder={Text("Buscá una plaza, dirección o ciudad")},singleLine=true,enabled=enabled,
                modifier=Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable,enabled).onFocusChanged {
                    fieldFocused=it.isFocused
                    if(it.isFocused)menuRequested=true
                })
            ExposedDropdownMenu(expanded=expanded,onDismissRequest={menuRequested=false},modifier=Modifier.heightIn(max=240.dp)) {
                hits.forEach { hit -> DropdownMenuItem(text={Text(hit.label)},onClick={
                    revision++;locatePending=false;search="";hits=emptyList();status=null;menuRequested=false
                    onValueChange(hit.label);onPointChange(com.onlygoodthings.shared.domain.GeoPoint(hit.latitude,hit.longitude));focusManager.clearFocus()
                }) }
            }
        }
        TextButton(onClick={
            if(gps.permission==LocationPermissionState.DENIED_FOREVER && session.gpsEnabled)gps.openSettings()
            revision++;search="";hits=emptyList();locationRequest++;locatePending=true
        },enabled=enabled && !locating) { Text(if(locating)"Buscando tu ubicación…" else "Usar mi ubicación actual") }
        status?.let { Text(it,color=OgtColors.muted) }
    }
}
