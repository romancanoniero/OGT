package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.map.AddressHit
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.awaitHere
import com.onlygoodthings.app.map.reverseGeocode
import com.onlygoodthings.app.map.searchAddress
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.BarrioMarker
import com.onlygoodthings.app.ui.components.CarSilhouette
import com.onlygoodthings.app.ui.components.OgtBarrioMap
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtGpsBanner
import com.onlygoodthings.app.ui.components.OgtLoaderOverlay
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PlacePickKind
import com.onlygoodthings.app.ui.components.PlacePickRow
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkedCar
import com.onlygoodthings.shared.domain.VehicleProfile
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ColorChoices = listOf(
    Triple("Blanco", "White", "#F4F4F5"),
    Triple("Negro", "Black", "#18181B"),
    Triple("Gris", "Gray", "#71717A"),
    Triple("Rojo", "Red", "#DC2626"),
    Triple("Azul", "Blue", "#2563EB"),
    Triple("Verde", "Green", "#059669"),
    Triple("Naranja", "Orange", "#EA580C"),
)

/** Marca dónde quedó el auto: GPS, dirección o mapa. El mapa solo aparece si lo piden. */
@Composable
fun ParkHereScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val session = LocalOgtSession.current
    val gps = LocalOgtLocation.current
    val copy = LocalOgtCopy.current
    val en = copy.lang == OgtLang.EN
    val scope = rememberCoroutineScope()
    var make by remember { mutableStateOf(session.vehicle.make) }
    var plate by remember { mutableStateOf(session.vehicle.plate) }
    var colorName by remember { mutableStateOf(session.vehicle.color.ifBlank { if (en) "Gray" else "Gris" }) }
    var colorHex by remember { mutableStateOf(session.vehicle.colorHex) }
    var carCommitted by remember {
        mutableStateOf(session.vehicle.isReady() && session.vehicles.any { it.id == session.selectedVehicleId })
    }
    var mode by remember { mutableStateOf<PlacePickKind?>(null) }
    var draft by remember { mutableStateOf<GeoPoint?>(null) }
    var address by remember { mutableStateOf("") }
    var addressQuery by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<AddressHit>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var placeReady by remember { mutableStateOf(false) }
    val vehicle = VehicleProfile(make = make.trim(), color = colorName, colorHex = colorHex, plate = plate.trim())
    val lang = copy.lang.code

    LaunchedEffect(mode, draft?.latitude, draft?.longitude) {
        val point = draft ?: return@LaunchedEffect
        if (mode != PlacePickKind.MAP) return@LaunchedEffect
        delay(700)
        runCatching { reverseGeocode(point, lang) }
            .onSuccess { hit -> if (hit != null) address = hit.label }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.parkHereTitle, onBack = onBack)
        ScreenColumn {
            OgtCaption(copy.parkHereHint)
            OgtSectionTitle(copy.vehicleTitle)
            OgtCaption(copy.vehicleWhy)
            if (session.vehicles.isNotEmpty()) {
                OgtCaption(copy.yourCars)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    session.vehicles.forEach { saved ->
                        Box(
                            Modifier.clickable {
                                session.selectVehicle(saved.id)
                                make = saved.make
                                plate = saved.plate
                                colorName = saved.color
                                colorHex = saved.colorHex
                                carCommitted = saved.isReady()
                            },
                        ) {
                            OgtPill(
                                saved.label(),
                                if (session.selectedVehicleId == saved.id) OgtColors.primary else OgtColors.sand,
                                if (session.selectedVehicleId == saved.id) androidx.compose.ui.graphics.Color.White else OgtColors.ink,
                            )
                        }
                    }
                    Box(Modifier.clickable {
                        make = ""
                        plate = ""
                        colorName = if (en) "Gray" else "Gris"
                        colorHex = "#71717A"
                        carCommitted = false
                        mode = null
                        placeReady = false
                    }) {
                        OgtPill(copy.addAnotherCar, OgtColors.mint, OgtColors.mintText)
                    }
                }
            }
            CarSilhouette(colorHex)
            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(copy.vehicleMakeHint) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(copy.vehiclePlateHint) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorChoices.forEach { (es, english, hex) ->
                    val label = if (en) english else es
                    Box(Modifier.clickable { colorName = label; colorHex = hex }) {
                        OgtPill(
                            label,
                            if (colorName == label || colorName == es || colorName == english) OgtColors.primary else OgtColors.sand,
                            if (colorName == label || colorName == es || colorName == english) androidx.compose.ui.graphics.Color.White else OgtColors.ink,
                        )
                    }
                }
            }
            if (!vehicle.isReady()) OgtCaption(copy.vehicleNeeded)
            Text(
                if (vehicle.isReady()) "${copy.selectedCar}: ${vehicle.label()}" else vehicle.label().ifBlank { "—" },
                fontWeight = FontWeight.SemiBold,
                color = OgtColors.ink,
            )
            OgtSecondaryButton(copy.saveCar, enabled = vehicle.color.isNotBlank()) {
                session.upsertVehicle(vehicle)
                carCommitted = true
            }

            OgtSectionTitle(copy.locateSection)
                PlacePickRow(
                    hereLabel = copy.locateHere,
                    addressLabel = copy.locateByAddress,
                    mapLabel = copy.locateOnMap,
                    selected = mode,
                    enabled = !busy,
                    onHere = {
                        mode = PlacePickKind.HERE
                        hits = emptyList()
                        scope.launch {
                            busy = true
                            error = null
                            val fix = awaitHere(session, gps)
                            if (fix == null) {
                                session.askLocationScope = true
                                placeReady = false
                                error = copy.needGps
                            } else {
                                draft = fix
                                val hit = runCatching { reverseGeocode(fix, lang) }.getOrNull()
                                address = hit?.label.orEmpty().ifBlank { "${fix.latitude}, ${fix.longitude}" }
                                placeReady = true
                            }
                            busy = false
                        }
                    },
                    onAddress = {
                        mode = PlacePickKind.ADDRESS
                        hits = emptyList()
                        if (address.isNotBlank() && addressQuery.isBlank()) addressQuery = address
                    },
                    onMap = {
                        mode = PlacePickKind.MAP
                        hits = emptyList()
                        if (draft == null) draft = session.here() ?: GeoPoint(-34.6037, -58.3816)
                        placeReady = draft != null
                    },
                )
                if (mode == PlacePickKind.HERE && !gps.hasLiveFix && session.here() == null) {
                    OgtGpsBanner()
                }
                if (mode == PlacePickKind.ADDRESS) {
                    OutlinedTextField(
                        value = addressQuery,
                        onValueChange = { addressQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(copy.addressHint) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                    )
                    OgtSecondaryButton(copy.searchAddress, enabled = !busy && addressQuery.trim().length >= 3) {
                        scope.launch {
                            busy = true
                            error = null
                            hits = runCatching { searchAddress(addressQuery, lang) }
                                .onFailure { error = it.message }
                                .getOrDefault(emptyList())
                            if (hits.isEmpty() && error == null) error = copy.noAddressHits
                            busy = false
                        }
                    }
                    hits.forEach { hit ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = hit.point
                                    address = hit.label
                                    addressQuery = hit.label
                                    hits = emptyList()
                                    placeReady = true
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            OgtCaption(hit.label)
                        }
                    }
                }
                if (mode == PlacePickKind.MAP) {
                    val center = draft ?: GeoPoint(-34.6037, -58.3816)
                    OgtBarrioMap(
                        center = center,
                        radiusMeters = 200,
                        markers = listOf(
                            BarrioMarker("parked", "CAR", copy.myCarPin, center.latitude, center.longitude),
                        ),
                        selectedId = "parked",
                        onSelect = {},
                        height = 240.dp,
                        onViewMoved = {
                            draft = it
                            placeReady = true
                        },
                    )
                    OgtCaption(copy.adjustOnMap)
                }
            if (placeReady && address.isNotBlank()) {
                if (mode == PlacePickKind.HERE) OgtPill(copy.placeFromGps)
                OgtCaption(address)
            }

            if (!error.isNullOrBlank()) OgtCaption(error!!)
            if (!placeReady) OgtCaption(copy.needPlace)
            OgtPrimaryButton(
                copy.confirmParkHere,
                enabled = placeReady && draft != null && !busy,
            ) {
                val point = draft ?: return@OgtPrimaryButton
                val stored = session.upsertVehicle(vehicle)
                session.rememberParked(
                    ParkedCar(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        parkedAtEpochMs = currentEpochMs(),
                        address = address.ifBlank { null },
                        vehicle = stored,
                    ),
                )
                onDone()
            }
            Spacer(Modifier.height(80.dp))
        }
    }
        OgtLoaderOverlay(
            visible = busy,
            label = if (mode == PlacePickKind.HERE) copy.locatingGps else if (mode == PlacePickKind.ADDRESS) copy.resolvingAddress else copy.loading,
        )
    }
}
