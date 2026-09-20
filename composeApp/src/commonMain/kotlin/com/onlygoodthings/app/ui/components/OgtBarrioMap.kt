package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.map.OgtLibreMap
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.domain.GeoPoint
import org.jetbrains.compose.resources.DrawableResource

/** Pin visible sobre el mapa (parking, mascota, trueque o el usuario). */
data class BarrioMarker(
    val id: String,
    val kind: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val avatar: DrawableResource? = null,
    /** Lo vio: badge de ojo en lugar del nombre. */
    val saw: Boolean = false,
    /** Escribió: badge de diálogo. */
    val wrote: Boolean = false,
    /** En mapa ampliado, los avistajes muestran badges y no el nombre. */
    val badgesInsteadOfName: Boolean = false,
)

private val MapWell = OgtColors.sand

/**
 * Mapa de la comunidad en Compose Multiplatform (Canvas + teselas Esri Light Gray).
 * Sin WebView y sin clave. El mismo composable corre en Android e iOS.
 */
@Composable
fun OgtBarrioMap(
    center: GeoPoint,
    radiusMeters: Int,
    markers: List<BarrioMarker>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 380.dp,
    fill: Boolean = false,
    onViewMoved: ((GeoPoint) -> Unit)? = null,
    onCameraInteract: (() -> Unit)? = null,
    interactive: Boolean = true,
    route: List<GeoPoint> = emptyList(),
    headingTo: GeoPoint? = null,
    fitPoints: List<GeoPoint> = emptyList(),
) {
    Box(
        modifier
            .fillMaxWidth()
            .then(if (fill) Modifier.fillMaxSize() else Modifier.height(height))
            .clip(RoundedCornerShape(if (fill) 0.dp else 28.dp))
            .background(MapWell),
    ) {
        OgtLibreMap(
            center = center,
            radiusMeters = radiusMeters,
            markers = markers,
            selectedId = selectedId,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize(),
            onViewMoved = onViewMoved,
            onCameraInteract = onCameraInteract,
            interactive = interactive,
            route = route,
            headingTo = headingTo,
            fitPoints = fitPoints,
        )
        Text(
            "© Esri · OpenStreetMap",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp),
            color = OgtColors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
