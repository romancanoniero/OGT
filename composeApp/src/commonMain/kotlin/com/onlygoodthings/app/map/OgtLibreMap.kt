package com.onlygoodthings.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_comment
import com.onlygoodthings.app.resources.qs_eye
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.BarrioMarker
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import org.jetbrains.compose.resources.painterResource
import com.onlygoodthings.shared.data.createTileHttpClient
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TILE_PX = 256
private const val TILE_CACHE_MAX = 160
/** Esri Light Gray: gris arena Quiet Studio, sin clave. */
private const val TILE_STYLE = "qs-esri-light"
private val MapWell = OgtColors.sand
private val RadiusFill = OgtColors.secondary.copy(alpha = 0.10f)
private val RadiusStroke = OgtColors.secondary
private val tileClient by lazy { createTileHttpClient() }
private val fetchMutex = Mutex()
private val tileSlots = Semaphore(8)
private val inflight = mutableSetOf<TileKey>()
/** Caché de proceso: al volver a Estacionar no se vuelven a bajar las teselas. */
private val tileCache = LinkedHashMap<TileKey, ImageBitmap>()

private data class TileKey(val style: String, val z: Int, val x: Int, val y: Int)

/** Cámara mutable: los gestos no pueden cerrar sobre un zoom viejo. */
private class MapCamera(lat: Double, lng: Double, zoom: Float) {
    var lat: Double = lat
    var lng: Double = lng
    var zoom: Float = zoom
    var followGps: Boolean = true
    var viewport: IntSize = IntSize.Zero
}

/**
 * Mapa raster Esri (OSM de respaldo) dibujado en Compose.
 * Sin WebView y sin clave de Google. El mismo composable corre en Android e iOS.
 */
@Composable
fun OgtLibreMap(
    center: GeoPoint,
    radiusMeters: Int,
    markers: List<BarrioMarker>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onViewMoved: ((GeoPoint) -> Unit)? = null,
    onCameraInteract: (() -> Unit)? = null,
    interactive: Boolean = true,
    route: List<GeoPoint> = emptyList(),
    headingTo: GeoPoint? = null,
    /** Si no está vacío, al cambiar recuadra estos puntos (origen + avistajes del día). */
    fitPoints: List<GeoPoint> = emptyList(),
) {
    val cam = remember {
        MapCamera(center.latitude, center.longitude, zoomForRadiusMeters(radiusMeters).toFloat().coerceIn(3f, 19f))
    }
    var viewLat by remember { mutableStateOf(cam.lat) }
    var viewLng by remember { mutableStateOf(cam.lng) }
    var zoom by remember { mutableStateOf(cam.zoom) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val tiles = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    var firstTile by remember { mutableStateOf(false) }
    val tileZ = floor(zoom.toDouble()).toInt().coerceIn(3, 19)
    val consumeScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available
        }
    }
    fun publishCamera() {
        viewLat = cam.lat
        viewLng = cam.lng
        zoom = cam.zoom
        viewport = cam.viewport
    }

    LaunchedEffect(center.latitude, center.longitude) {
        if (fitPoints.isNotEmpty()) return@LaunchedEffect
        val drift = GeoMath.haversineMeters(GeoPoint(cam.lat, cam.lng), center)
        if (cam.followGps || drift > 80) {
            cam.lat = center.latitude
            cam.lng = center.longitude
            publishCamera()
        }
    }
    LaunchedEffect(radiusMeters) {
        if (cam.followGps && fitPoints.isEmpty()) {
            cam.zoom = zoomForRadiusMeters(radiusMeters).toFloat().coerceIn(3f, 19f)
            publishCamera()
        }
    }
    val fitKey = remember(fitPoints) {
        fitPoints.joinToString(";") { "${it.latitude},${it.longitude}" }
    }
    LaunchedEffect(fitKey, viewport.width, viewport.height) {
        if (fitPoints.isEmpty() || viewport.width < 8 || viewport.height < 8) return@LaunchedEffect
        val frame = cameraToFit(fitPoints, viewport)
        cam.lat = frame.lat
        cam.lng = frame.lng
        cam.zoom = frame.zoom
        cam.followGps = true
        publishCamera()
    }

    val visible = remember(viewLat, viewLng, tileZ, viewport) {
        visibleTiles(viewLat, viewLng, tileZ, viewport)
    }
    LaunchedEffect(visible) {
        visible.forEach { key ->
            cachedTile(key)?.let { tiles[key] = it; firstTile = true }
        }
        val wanted = (visible + visible.mapNotNull { parentKey(it) }).distinct()
        val missing = wanted.filter { cachedTile(it) == null }
        if (missing.isEmpty()) return@LaunchedEffect
        coroutineScope {
            missing.map { key ->
                async(Dispatchers.Default) {
                    loadTile(key)?.let {
                        rememberTile(key, it)
                        tiles[key] = it
                        firstTile = true
                    }
                }
            }.awaitAll()
        }
    }

    val density = LocalDensity.current
    val gestures = if (!interactive) {
        Modifier
    } else {
        Modifier
            .nestedScroll(consumeScroll)
            .pointerInput(cam) {
                fun zoomAt(centroid: Offset, factor: Float) {
                    val vp = cam.viewport
                    if (vp.width <= 0) return
                    val before = screenToGeo(centroid.x, centroid.y, cam.lng, cam.lat, cam.zoom, vp)
                    cam.zoom = (cam.zoom + ln(factor.toDouble()) / ln(2.0)).toFloat().coerceIn(3f, 19f)
                    val after = screenToGeo(centroid.x, centroid.y, cam.lng, cam.lat, cam.zoom, vp)
                    cam.lat += before.latitude - after.latitude
                    cam.lng += before.longitude - after.longitude
                }
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    cam.followGps = false
                    if (zoomChange != 1f) zoomAt(centroid, zoomChange)
                    if (pan != Offset.Zero) {
                        val mpp = metersPerPixel(cam.lat, cam.zoom)
                        cam.lng += (pan.x * mpp) / (111_320.0 * cos(cam.lat * PI / 180.0)).coerceAtLeast(1.0)
                        cam.lat -= (pan.y * mpp) / 110_540.0
                    }
                    publishCamera()
                    onCameraInteract?.invoke()
                    onViewMoved?.invoke(GeoPoint(cam.lat, cam.lng))
                }
            }
            .pointerInput(cam) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        cam.followGps = false
                        val vp = cam.viewport
                        if (vp.width > 0) {
                            val before = screenToGeo(tap.x, tap.y, cam.lng, cam.lat, cam.zoom, vp)
                            cam.zoom = (cam.zoom + 1f).coerceIn(3f, 19f)
                            val after = screenToGeo(tap.x, tap.y, cam.lng, cam.lat, cam.zoom, vp)
                            cam.lat += before.latitude - after.latitude
                            cam.lng += before.longitude - after.longitude
                        }
                        publishCamera()
                        onCameraInteract?.invoke()
                        onViewMoved?.invoke(GeoPoint(cam.lat, cam.lng))
                    },
                )
            }
    }
    Box(
        modifier
            .background(MapWell)
            .onSizeChanged {
                cam.viewport = it
                viewport = it
            }
            .then(gestures),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (viewport == IntSize.Zero) return@Canvas
            val scale = 2.0.pow((zoom - tileZ).toDouble()).toFloat()
            val origin = worldOrigin(viewLng, viewLat, tileZ, size.width, size.height)
            val cx = size.width / 2f
            val cy = size.height / 2f
            visible.forEach { key ->
                val left = ((key.x * TILE_PX - origin.x).toFloat() - cx) * scale + cx
                val top = ((key.y * TILE_PX - origin.y).toFloat() - cy) * scale + cy
                val side = (TILE_PX * scale).roundToInt().coerceAtLeast(1)
                val dst = IntOffset(left.roundToInt(), top.roundToInt())
                val dstSize = IntSize(side, side)
                val exact = tiles[key] ?: cachedTile(key)
                if (exact != null) {
                    drawImage(exact, dstOffset = dst, dstSize = dstSize)
                } else {
                    drawAncestorTile(key, dst, dstSize)
                }
            }
            val radiusPx = (radiusMeters / metersPerPixel(viewLat, zoom)).toFloat()
            val mid = Offset(size.width / 2f, size.height / 2f)
            drawCircle(RadiusFill, radius = radiusPx, center = mid)
            drawCircle(
                color = RadiusStroke,
                radius = radiusPx,
                center = mid,
                style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
            )
            drawMovement(route, headingTo, viewLng, viewLat, zoom, viewport, tileZ)
        }
        if (!firstTile) {
            Text(
                "Cargando mapa…",
                modifier = Modifier.align(Alignment.Center),
                color = OgtColors.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        markers.forEach { marker ->
            val pos = screenOffset(marker.longitude, marker.latitude, viewLng, viewLat, zoom, viewport, tileZ)
            if (pos == null) return@forEach
            val selected = marker.id == selectedId
            val pinDp = if (selected) 36.dp else 30.dp
            val pinPx = with(density) { pinDp.roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(pos.x - pinPx / 2, pos.y - pinPx) }
                    .clickable(enabled = marker.id != "me") { onSelect(marker.id) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                MapPin(marker, selected)
            }
        }
    }
}

@Composable
private fun MapPin(marker: BarrioMarker, selected: Boolean) {
    val fill = when {
        marker.kind == "SIGHT" -> OgtColors.sunset
        selected || marker.kind == "CAR" || marker.kind == "ME" || marker.kind == "PARKING" || marker.kind == "ORIGIN" -> OgtColors.secondary
        else -> OgtColors.tertiary
    }
    val named = selected || marker.kind in setOf("ME", "CAR", "SIGHT", "ORIGIN")
    val showBadges = marker.badgesInsteadOfName && marker.kind == "SIGHT" && (marker.saw || marker.wrote)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showBadges) {
            Row(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x140A0A0B))
                    .clip(RoundedCornerShape(16.dp))
                    .background(OgtColors.surface)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (marker.saw) {
                    OgtStitchIcon(Res.drawable.qs_eye, "Lo vio", size = 12.dp, tint = OgtColors.secondary)
                }
                if (marker.wrote) {
                    OgtStitchIcon(Res.drawable.qs_comment, "Escribió", size = 12.dp, tint = OgtColors.secondary)
                }
            }
        } else if (named) {
            Text(
                marker.label,
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x140A0A0B))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = OgtColors.ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        val pin = if (selected) 40.dp else 34.dp
        Box(
            Modifier
                .size(pin)
                .shadow(6.dp, CircleShape, ambientColor = Color(0x140A0A0B))
                .clip(CircleShape)
                .background(fill)
                .then(if (marker.avatar != null) Modifier.border(2.dp, fill, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val art = marker.avatar
            if (art != null) {
                Image(
                    painter = painterResource(art),
                    contentDescription = marker.label,
                    modifier = Modifier.size(pin).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                com.onlygoodthings.app.ui.components.OgtStitchIcon(
                    art = com.onlygoodthings.app.ui.components.pinStitchArt(marker.kind),
                    contentDescription = marker.label,
                    size = if (selected) 22.dp else 18.dp,
                    tint = Color.White,
                )
            }
        }
    }
}

private fun visibleTiles(lat: Double, lng: Double, zoom: Int, viewport: IntSize): List<TileKey> {
    if (viewport.width <= 0 || viewport.height <= 0) return emptyList()
    val origin = worldOrigin(lng, lat, zoom, viewport.width.toFloat(), viewport.height.toFloat())
    val n = 1 shl zoom
    val x0 = floor(origin.x / TILE_PX).toInt()
    val y0 = floor(origin.y / TILE_PX).toInt()
    val x1 = floor((origin.x + viewport.width) / TILE_PX).toInt()
    val y1 = floor((origin.y + viewport.height) / TILE_PX).toInt()
    val keys = ArrayList<TileKey>((x1 - x0 + 1) * (y1 - y0 + 1))
    for (x in x0..x1) {
        for (y in y0..y1) {
            if (y in 0 until n) keys += TileKey(TILE_STYLE, zoom, ((x % n) + n) % n, y)
        }
    }
    return keys
}

private data class WorldOrigin(val x: Double, val y: Double)

private fun worldOrigin(lng: Double, lat: Double, zoom: Int, width: Float, height: Float): WorldOrigin {
    val cx = lonToX(lng, zoom)
    val cy = latToY(lat, zoom)
    return WorldOrigin(cx - width / 2.0, cy - height / 2.0)
}

private fun screenOffset(
    lng: Double,
    lat: Double,
    viewLng: Double,
    viewLat: Double,
    zoom: Float,
    viewport: IntSize,
    tileZ: Int,
    clip: Boolean = true,
): IntOffset? {
    if (viewport.width <= 0) return null
    val scale = 2.0.pow((zoom - tileZ).toDouble())
    val origin = worldOrigin(viewLng, viewLat, tileZ, viewport.width.toFloat(), viewport.height.toFloat())
    val cx = viewport.width / 2.0
    val cy = viewport.height / 2.0
    val x = ((lonToX(lng, tileZ) - origin.x - cx) * scale + cx).roundToInt()
    val y = ((latToY(lat, tileZ) - origin.y - cy) * scale + cy).roundToInt()
    if (clip && (x !in -120..(viewport.width + 120) || y !in -120..(viewport.height + 120))) return null
    return IntOffset(x, y)
}

private fun DrawScope.drawMovement(
    route: List<GeoPoint>,
    headingTo: GeoPoint?,
    viewLng: Double,
    viewLat: Double,
    zoom: Float,
    viewport: IntSize,
    tileZ: Int,
) {
    fun point(geo: GeoPoint): Offset? {
        val off = screenOffset(geo.longitude, geo.latitude, viewLng, viewLat, zoom, viewport, tileZ, clip = false)
            ?: return null
        return Offset(off.x.toFloat(), off.y.toFloat())
    }
    val pts = route.mapNotNull { point(it) }
    if (pts.size >= 2) {
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path,
            color = RadiusStroke,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        pts.zipWithNext { from, to -> drawArrowHead(from, to, RadiusStroke) }
    }
    val from = pts.lastOrNull() ?: return
    val dest = headingTo?.let { point(it) } ?: return
    val dash = Path().apply {
        moveTo(from.x, from.y)
        lineTo(dest.x, dest.y)
    }
    drawPath(
        dash,
        color = RadiusStroke.copy(alpha = 0.55f),
        style = Stroke(
            width = 3.5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        ),
    )
    drawArrowHead(from, dest, RadiusStroke.copy(alpha = 0.75f))
}

/** Punta a mitad del tramo, lejos de los pins de origen y destino. */
private fun DrawScope.drawArrowHead(from: Offset, to: Offset, color: Color) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 24f) return
    val ux = dx / len
    val uy = dy / len
    val tip = Offset(from.x + ux * (len * 0.55f), from.y + uy * (len * 0.55f))
    val compact = len < 72f
    val back = if (compact) 13f else 18f
    val wing = if (compact) 9f else 12f
    val base = Offset(tip.x - ux * back, tip.y - uy * back)
    val left = Offset(base.x - uy * wing, base.y + ux * wing)
    val right = Offset(base.x + uy * wing, base.y - ux * wing)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(head, color)
}

/** Invierte el offset de pantalla a lat/lng con zoom fraccionario. */
private fun screenToGeo(
    x: Float,
    y: Float,
    viewLng: Double,
    viewLat: Double,
    zoom: Float,
    viewport: IntSize,
): GeoPoint {
    val tileZ = floor(zoom.toDouble()).toInt().coerceIn(3, 19)
    val scale = 2.0.pow((zoom - tileZ).toDouble())
    val origin = worldOrigin(viewLng, viewLat, tileZ, viewport.width.toFloat(), viewport.height.toFloat())
    val cx = viewport.width / 2.0
    val cy = viewport.height / 2.0
    val worldX = origin.x + (x - cx) / scale + cx
    val worldY = origin.y + (y - cy) / scale + cy
    return GeoPoint(yToLat(worldY, tileZ), xToLon(worldX, tileZ))
}

private fun lonToX(lon: Double, zoom: Int): Double {
    val n = (1 shl zoom).toDouble()
    return (lon + 180.0) / 360.0 * n * TILE_PX
}

private fun latToY(lat: Double, zoom: Int): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    val latRad = clamped * PI / 180.0
    val n = (1 shl zoom).toDouble()
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * TILE_PX
}

private fun metersPerPixel(lat: Double, zoom: Float): Double =
    156543.03392 * cos(lat * PI / 180.0) / 2.0.pow(zoom.toDouble())

private data class FittedCamera(val lat: Double, val lng: Double, val zoom: Float)

/** Centro y zoom para que todos los puntos quepan, con aire para chrome y ficha. */
private fun cameraToFit(points: List<GeoPoint>, viewport: IntSize): FittedCamera {
    val lats = points.map { it.latitude }
    val lngs = points.map { it.longitude }
    val minLat = lats.minOrNull() ?: 0.0
    val maxLat = lats.maxOrNull() ?: 0.0
    val minLng = lngs.minOrNull() ?: 0.0
    val maxLng = lngs.maxOrNull() ?: 0.0
    val lat = (minLat + maxLat) / 2.0
    val lng = (minLng + maxLng) / 2.0
    val sameSpot = points.size == 1 || (maxLat - minLat < 1e-6 && maxLng - minLng < 1e-6)
    if (sameSpot) return FittedCamera(lat, lng, 15.5f)
    val south = GeoPoint(minLat, lng)
    val north = GeoPoint(maxLat, lng)
    val west = GeoPoint(lat, minLng)
    val east = GeoPoint(lat, maxLng)
    val spanLatM = GeoMath.haversineMeters(south, north).coerceAtLeast(48.0)
    val spanLngM = GeoMath.haversineMeters(west, east).coerceAtLeast(48.0)
    val usableW = viewport.width * 0.72f
    val usableH = viewport.height * 0.52f
    fun zoomForSpan(spanM: Double, pixels: Float): Float {
        if (pixels <= 8f) return 15f
        val mpp = spanM / pixels
        val z = ln(156543.03392 * cos(lat * PI / 180.0) / mpp) / ln(2.0)
        return z.toFloat()
    }
    val zoom = min(zoomForSpan(spanLngM, usableW), zoomForSpan(spanLatM, usableH)).coerceIn(10f, 18f)
    return FittedCamera(lat, lng, zoom)
}

private fun xToLon(worldX: Double, zoom: Int): Double {
    val n = (1 shl zoom).toDouble()
    return worldX / (n * TILE_PX) * 360.0 - 180.0
}

private fun yToLat(worldY: Double, zoom: Int): Double {
    val n = (1 shl zoom).toDouble()
    val yNorm = (worldY / (n * TILE_PX)).coerceIn(0.0, 1.0)
    val latRad = atan(sinh(PI * (1.0 - 2.0 * yNorm)))
    return (latRad * 180.0 / PI).coerceIn(-85.05112878, 85.05112878)
}

private fun sinh(x: Double): Double {
    val e = exp(x)
    return (e - 1.0 / e) / 2.0
}

private fun cachedTile(key: TileKey): ImageBitmap? = withTileCacheLock { tileCache[key] }

private fun rememberTile(key: TileKey, bmp: ImageBitmap) {
    withTileCacheLock {
        tileCache[key] = bmp
        while (tileCache.size > TILE_CACHE_MAX) {
            val oldest = tileCache.entries.firstOrNull()?.key ?: break
            tileCache.remove(oldest)
        }
    }
}

private fun parentKey(key: TileKey): TileKey? {
    if (key.z <= 3) return null
    return TileKey(key.style, key.z - 1, key.x / 2, key.y / 2)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAncestorTile(
    key: TileKey,
    dst: IntOffset,
    dstSize: IntSize = IntSize(TILE_PX, TILE_PX),
) {
    var z = key.z - 1
    var shift = 1
    while (z >= 3) {
        val ancestor = TileKey(key.style, z, key.x shr shift, key.y shr shift)
        val bmp = cachedTile(ancestor)
        if (bmp != null) {
            val srcSize = (TILE_PX shr shift).coerceAtLeast(1)
            val mask = (1 shl shift) - 1
            val srcX = (key.x and mask) * srcSize
            val srcY = (key.y and mask) * srcSize
            drawImage(
                image = bmp,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcSize, srcSize),
                dstOffset = dst,
                dstSize = dstSize,
            )
            return
        }
        z -= 1
        shift += 1
    }
}

private suspend fun loadTile(key: TileKey): ImageBitmap? = withContext(Dispatchers.Default) {
    cachedTile(key)?.let { return@withContext it }
    fetchMutex.withLock {
        if (!inflight.add(key)) return@withContext cachedTile(key)
    }
    tileSlots.acquire()
    try {
        cachedTile(key)?.let { return@withContext it }
        // Quiet Studio: Esri Light Gray primero; Carto Light si Esri no responde.
        val urls = listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/${key.z}/${key.y}/${key.x}",
            "https://a.basemaps.cartocdn.com/light_all/${key.z}/${key.x}/${key.y}.png",
            "https://b.basemaps.cartocdn.com/light_all/${key.z}/${key.x}/${key.y}.png",
        )
        for (url in urls) {
            val bytes = runCatching {
                val response = tileClient.get(url) {
                    header("User-Agent", "OnlyGoodThings/0.1 (Compose Multiplatform; parking map)")
                    header("Referer", "https://onlygoodthings.app/")
                }
                if (response.status.isSuccess()) response.bodyAsBytes() else null
            }.getOrNull() ?: continue
            decodeTileBitmap(bytes)?.let { return@withContext it }
        }
        null
    } finally {
        tileSlots.release()
        fetchMutex.withLock { inflight.remove(key) }
    }
}
