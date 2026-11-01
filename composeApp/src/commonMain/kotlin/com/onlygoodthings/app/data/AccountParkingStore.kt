package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.ParkedCar
import com.onlygoodthings.shared.domain.VehicleProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class AccountParking(
    val vehicles: List<VehicleProfile> = emptyList(),
    val selectedVehicleId: String = "",
    val parkedCar: ParkedCar? = null,
)

/** Legacy device-wide values have no proven owner. Leave them untouched and unassigned. */
internal class AccountParkingStore(private val read: () -> String, private val write: (String) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun accounts(): Map<String, AccountParking> =
        if (read().isBlank()) emptyMap() else json.decodeFromString(read())

    fun load(owner: String): AccountParking =
        if (owner.isBlank()) AccountParking() else accounts()[owner] ?: AccountParking()

    fun save(owner: String, parking: AccountParking) {
        if (owner.isBlank()) return
        // A malformed existing record must never be replaced with an empty map.
        write(json.encodeToString(accounts() + (owner to parking)))
    }

    fun remove(owner: String) {
        if (owner.isNotBlank()) write(json.encodeToString(accounts() - owner))
    }
}
