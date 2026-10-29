package com.onlygoodthings.app.map

import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapZoomTest {
    // Public city coordinates, unrelated to a user's saved parking position.
    private val lat = -34.6
    private val lng = -58.4
    private val viewport = IntSize(1179, 1700)

    @Test fun zoomingPastStreetCoverageKeepsUsingRealCartography() {
        for (zoom in listOf(16f, 16.5f, 17f, 18f, 19f)) {
            val tiles = visibleTiles(lat, lng, zoom, viewport)
            assertTrue(tiles.isNotEmpty())
            assertTrue(tiles.all { it.z == 16 }, "Must not request the HTTP-200 placeholder at zoom $zoom")
        }
        // Overzoom narrows the requested area; it must not fetch a whole L16
        // viewport and magnify all of those off-screen tiles.
        assertTrue(visibleTiles(lat, lng, 19f, viewport).size <= 4)
    }

    @Test fun everyViewportCornerHasATileDuringPinchAndPan() {
        for (zoom in listOf(3f, 12.25f, 15.99f, 16f, 16.75f, 18.5f, 19f)) {
            for (longitude in listOf(lng, lng + .002, lng - .002)) {
                val tiles = visibleTiles(lat, longitude, zoom, viewport)
                val z = rasterTileZoom(zoom)
                val n = (1 shl z).toDouble()
                for ((x, y) in listOf(0f to 0f, 1178f to 0f, 0f to 1699f, 1178f to 1699f)) {
                    val p = screenToGeo(x, y, longitude, lat, zoom, viewport)
                    val tx = floor((p.longitude + 180) / 360 * n).toInt()
                    val ty = floor((1 - asinh(tan(p.latitude * PI / 180)) / PI) / 2 * n).toInt()
                    assertTrue(tiles.any { it.x == tx && it.y == ty }, "Uncovered corner at $zoom / $longitude")
                }
            }
        }
    }

    @Test fun markersAndGestureAnchorsStayAlignedWithTheMapAtHighZoom() {
        for (zoom in listOf(15.5f, 16f, 16.9f, 17f, 18.25f, 19f)) {
            for ((x, y) in listOf(0f to 0f, 590f to 850f, 1000f to 1500f)) {
                val p = screenToGeo(x, y, lng, lat, zoom, viewport)
                val pixel = screenOffset(p.longitude, p.latitude, lng, lat, zoom, viewport, rasterTileZoom(zoom))!!
                assertEquals(x.toInt(), pixel.x)
                assertEquals(y.toInt(), pixel.y)
            }
        }
    }

    @Test fun overviewZoomAndEmptyLayoutRemainSupported() {
        assertEquals(3, visibleTiles(lat, lng, 3f, viewport).first().z)
        assertEquals(15, visibleTiles(lat, lng, 15.5f, viewport).first().z)
        assertTrue(visibleTiles(lat, lng, 19f, IntSize.Zero).isEmpty())
    }
}
