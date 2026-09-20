package com.onlygoodthings.shared.data

import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingCompleteResult
import com.onlygoodthings.shared.domain.ParkingHandoff
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.protocol.frames.LocationRole

interface ParkingRepository {
    suspend fun publishVacancy(
        location: GeoPoint,
        ttlMinutes: Int,
        notes: String?,
        vehicleLabel: String? = null,
        ownerLocation: GeoPoint? = null,
        leftoverNow: Boolean = false,
    ): ParkingSpot
    suspend fun nearby(location: GeoPoint, radiusMeters: Int): List<ParkingSpot>
    /** Anota interés durante la ventana; no adjudica la plaza. */
    suspend fun expressInterest(spotId: String, location: GeoPoint): ParkingSpot
    suspend fun claim(spotId: String, expectedVersion: Int, location: GeoPoint): ParkingSpot
    suspend fun tick(spotId: String, location: GeoPoint, role: LocationRole?, speedMps: Double?): ParkingSpot
    suspend fun complete(spotId: String, location: GeoPoint): ParkingCompleteResult
    suspend fun cancel(spotId: String): ParkingSpot
    /** El buscador ya estacionó en otro lado: suelta la cesión y la plaza vuelve FCFS. */
    suspend fun foundOtherPlace(spotId: String): ParkingSpot
    suspend fun active(): List<ParkingSpot>
    suspend fun history(): List<ParkingHandoff>
    suspend fun get(spotId: String): ParkingSpot?
}
