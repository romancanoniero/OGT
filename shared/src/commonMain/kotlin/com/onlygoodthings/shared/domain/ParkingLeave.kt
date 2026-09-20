package com.onlygoodthings.shared.domain

/**
 * El pin se puede olvidar solo. El aviso a la comunidad no.
 * Caminar al trabajo no cuenta: hace falta velocidad de auto o un pin viejo.
 */
object ParkingLeaveRules {
    const val NEAR_CAR_METERS = 40.0
    const val DEPART_METERS = 55.0
    const val FORGET_METERS = 180.0
    const val STALE_ASK_MS = 18L * 60 * 60 * 1000
    const val STALE_FORGET_MS = 24L * 60 * 60 * 1000
}

enum class ParkedPresenceAction {
    NONE,
    MARK_NEAR,
    ASK_LEAVE,
    FORGET_SILENT,
    ASK_STALE,
    FORGET_STALE,
}

data class ParkedPresenceTick(
    val metersFromCar: Double,
    val speedMps: Float?,
    val nowEpochMs: Long,
    val lastConfirmEpochMs: Long,
    val wasNearCar: Boolean,
    val sawVehicleDepart: Boolean,
    val leaveAsked: Boolean,
    val hasActiveYield: Boolean,
    val staleAsked: Boolean,
)

fun decideParkedPresence(tick: ParkedPresenceTick): ParkedPresenceAction {
    if (tick.hasActiveYield) return ParkedPresenceAction.NONE
    val parkedAge = tick.nowEpochMs - tick.lastConfirmEpochMs
    if (tick.lastConfirmEpochMs > 0L && parkedAge >= ParkingLeaveRules.STALE_FORGET_MS) {
        return ParkedPresenceAction.FORGET_STALE
    }
    val driving = (tick.speedMps ?: 0f) >= ParkingMotionRules.VEHICLE_SPEED_MPS
    val departed = tick.sawVehicleDepart ||
        (tick.wasNearCar && driving && tick.metersFromCar >= ParkingLeaveRules.DEPART_METERS)
    if (departed && tick.metersFromCar >= ParkingLeaveRules.FORGET_METERS) {
        return ParkedPresenceAction.FORGET_SILENT
    }
    if (departed && !tick.leaveAsked) return ParkedPresenceAction.ASK_LEAVE
    if (tick.lastConfirmEpochMs > 0L && parkedAge >= ParkingLeaveRules.STALE_ASK_MS && !tick.staleAsked) {
        return ParkedPresenceAction.ASK_STALE
    }
    if (tick.metersFromCar <= ParkingLeaveRules.NEAR_CAR_METERS) return ParkedPresenceAction.MARK_NEAR
    return ParkedPresenceAction.NONE
}
