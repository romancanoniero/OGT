package com.onlygoodthings.shared.domain

/**
 * Cromo de Estacionar: una sola lectura de pin + cesión viva.
 * La pantalla no decide a ojo qué tile, card o radar mostrar.
 */
data class ParkingChrome(
    val seeking: Boolean,
    val assignedToMe: Boolean,
    val iAmYielding: Boolean,
    val parkedEnabled: Boolean,
    val findParked: Boolean,
    val yieldEnabled: Boolean,
    val yieldSelected: Boolean,
    val showYield: Boolean,
    val showSearch: Boolean,
    val searchEnabled: Boolean,
    val showParkedCard: Boolean,
    val showRadiusChips: Boolean,
    val showNearbyList: Boolean,
    val showMap: Boolean,
    val showNotifyingCopy: Boolean,
    val showCancelYield: Boolean,
    val showOwnerHandoff: Boolean,
    val showAssignedCard: Boolean,
)

fun parkingChrome(
    parked: Boolean,
    hasGps: Boolean,
    busy: Boolean,
    searching: Boolean,
    liveStatus: ParkingStatus?,
    liveOwnerIsMe: Boolean,
    liveClaimantIsMe: Boolean,
): ParkingChrome {
    val assignedToMe = liveClaimantIsMe && liveStatus == ParkingStatus.CLAIMED
    val iAmYielding = liveOwnerIsMe &&
        liveStatus in listOf(ParkingStatus.AVAILABLE, ParkingStatus.CLAIMED)
    val seeking = !parked
    val notifyingYield = iAmYielding && liveStatus == ParkingStatus.AVAILABLE
    return ParkingChrome(
        seeking = seeking,
        assignedToMe = assignedToMe,
        iAmYielding = iAmYielding,
        parkedEnabled = !busy && !parked,
        findParked = parked,
        yieldEnabled = !busy && !iAmYielding,
        yieldSelected = iAmYielding,
        showYield = !assignedToMe,
        showSearch = seeking && !assignedToMe && !iAmYielding,
        searchEnabled = seeking && !busy && hasGps && !searching && !iAmYielding,
        showParkedCard = parked && !iAmYielding,
        showRadiusChips = seeking && !assignedToMe && !iAmYielding,
        showNearbyList = seeking && !assignedToMe && !iAmYielding,
        showMap = !notifyingYield,
        showNotifyingCopy = false,
        showCancelYield = iAmYielding,
        showOwnerHandoff = iAmYielding && liveStatus == ParkingStatus.CLAIMED,
        showAssignedCard = assignedToMe,
    )
}
