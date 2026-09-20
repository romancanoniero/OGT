package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ParkingLeaveTest {

    private fun tick(
        meters: Double,
        speed: Float? = null,
        now: Long = 10_000L,
        confirm: Long = 1_000L,
        wasNear: Boolean = false,
        departed: Boolean = false,
        leaveAsked: Boolean = false,
        yielding: Boolean = false,
        staleAsked: Boolean = false,
    ) = ParkedPresenceTick(
        metersFromCar = meters,
        speedMps = speed,
        nowEpochMs = now,
        lastConfirmEpochMs = confirm,
        wasNearCar = wasNear,
        sawVehicleDepart = departed,
        leaveAsked = leaveAsked,
        hasActiveYield = yielding,
        staleAsked = staleAsked,
    )

    @Test
    fun caminarLejosNoOlvidaElPin() {
        assertEquals(ParkedPresenceAction.MARK_NEAR, decideParkedPresence(tick(20.0, speed = 1.2f)))
        assertEquals(
            ParkedPresenceAction.NONE,
            decideParkedPresence(tick(200.0, speed = 1.4f, wasNear = true)),
        )
    }

    @Test
    fun alArrancarPreguntaSiAvisamos() {
        assertEquals(
            ParkedPresenceAction.ASK_LEAVE,
            decideParkedPresence(tick(70.0, speed = 8f, wasNear = true)),
        )
    }

    @Test
    fun siSeFueCalladoOlvidaElPin() {
        assertEquals(
            ParkedPresenceAction.FORGET_SILENT,
            decideParkedPresence(tick(200.0, departed = true, leaveAsked = true, wasNear = true)),
        )
    }

    @Test
    fun conCesionActivaNoTocaElPin() {
        assertEquals(
            ParkedPresenceAction.NONE,
            decideParkedPresence(tick(250.0, departed = true, yielding = true, wasNear = true)),
        )
    }

    @Test
    fun pinViejoPreguntaYDespuesOlvida() {
        val confirm = 1_000L
        assertEquals(
            ParkedPresenceAction.ASK_STALE,
            decideParkedPresence(tick(30.0, now = confirm + ParkingLeaveRules.STALE_ASK_MS + 1, confirm = confirm)),
        )
        assertEquals(
            ParkedPresenceAction.FORGET_STALE,
            decideParkedPresence(tick(30.0, now = confirm + ParkingLeaveRules.STALE_FORGET_MS + 1, confirm = confirm)),
        )
    }

    @Test
    fun noRepiteLaPreguntaDePartida() {
        assertEquals(
            ParkedPresenceAction.NONE,
            decideParkedPresence(tick(80.0, speed = 8f, wasNear = true, leaveAsked = true, departed = true)),
        )
    }
}
