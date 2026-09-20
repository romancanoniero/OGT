package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingChromeTest {

    private fun chrome(
        parked: Boolean = false,
        hasGps: Boolean = true,
        busy: Boolean = false,
        searching: Boolean = false,
        liveStatus: ParkingStatus? = null,
        liveOwnerIsMe: Boolean = false,
        liveClaimantIsMe: Boolean = false,
    ) = parkingChrome(parked, hasGps, busy, searching, liveStatus, liveOwnerIsMe, liveClaimantIsMe)

    @Test
    fun buscandoSinPinPuedeEstacionarCederYBuscar() {
        val ui = chrome()
        assertTrue(ui.seeking)
        assertTrue(ui.parkedEnabled)
        assertTrue(ui.yieldEnabled)
        assertTrue(ui.showYield)
        assertTrue(ui.showSearch)
        assertTrue(ui.searchEnabled)
        assertTrue(ui.showRadiusChips)
        assertTrue(ui.showNearbyList)
        assertFalse(ui.findParked)
        assertFalse(ui.showParkedCard)
        assertFalse(ui.iAmYielding)
        assertFalse(ui.assignedToMe)
    }

    @Test
    fun estacionadoMuestraDondeLoDejeYOcultaElRadar() {
        val ui = chrome(parked = true)
        assertFalse(ui.seeking)
        assertFalse(ui.parkedEnabled)
        assertTrue(ui.findParked)
        assertTrue(ui.yieldEnabled)
        assertTrue(ui.showParkedCard)
        assertFalse(ui.showSearch)
        assertFalse(ui.searchEnabled)
        assertFalse(ui.showRadiusChips)
        assertFalse(ui.showNearbyList)
    }

    @Test
    fun cederSinHaberMarcadoElPinOlvidaElEstacionadoYAvisa() {
        val ui = chrome(parked = false, liveStatus = ParkingStatus.AVAILABLE, liveOwnerIsMe = true)
        assertTrue(ui.seeking)
        assertTrue(ui.iAmYielding)
        assertTrue(ui.yieldSelected)
        assertFalse(ui.yieldEnabled)
        assertTrue(ui.showYield)
        assertTrue(ui.showCancelYield)
        assertFalse(ui.showMap)
        assertFalse(ui.showNotifyingCopy)
        assertFalse(ui.showOwnerHandoff)
        assertFalse(ui.showParkedCard)
        assertFalse(ui.showSearch)
        assertFalse(ui.showNearbyList)
        assertFalse(ui.showRadiusChips)
        assertFalse(ui.assignedToMe)
    }

    @Test
    fun siAunTuvieraPinMientrasCedeNoCompiteConLoDejasteAca() {
        val ui = chrome(parked = true, liveStatus = ParkingStatus.AVAILABLE, liveOwnerIsMe = true)
        assertTrue(ui.iAmYielding)
        assertFalse(ui.showParkedCard)
        assertFalse(ui.showSearch)
        assertTrue(ui.findParked)
        assertFalse(ui.parkedEnabled)
    }

    @Test
    fun lugarAsignadoAlBuscadorOcultaCederYElRadar() {
        val ui = chrome(liveStatus = ParkingStatus.CLAIMED, liveClaimantIsMe = true)
        assertTrue(ui.assignedToMe)
        assertTrue(ui.showAssignedCard)
        assertFalse(ui.showYield)
        assertFalse(ui.showSearch)
        assertFalse(ui.showNearbyList)
        assertFalse(ui.showRadiusChips)
        assertFalse(ui.iAmYielding)
        assertFalse(ui.showOwnerHandoff)
    }

    @Test
    fun duenioConVecinoEnCaminoSigueEnCesionNoEnAsignado() {
        val ui = chrome(liveStatus = ParkingStatus.CLAIMED, liveOwnerIsMe = true)
        assertTrue(ui.iAmYielding)
        assertTrue(ui.showOwnerHandoff)
        assertTrue(ui.showMap)
        assertTrue(ui.showYield)
        assertFalse(ui.assignedToMe)
        assertFalse(ui.showAssignedCard)
        assertFalse(ui.yieldEnabled)
    }

    @Test
    fun sinGpsNoBuscaPeroCederSigueHabilitadoParaMostrarError() {
        val ui = chrome(hasGps = false)
        assertTrue(ui.yieldEnabled)
        assertFalse(ui.searchEnabled)
        assertTrue(ui.showSearch)
    }

    @Test
    fun ocupadoApagaLosTilesDeAccion() {
        val ui = chrome(busy = true)
        assertFalse(ui.parkedEnabled)
        assertFalse(ui.yieldEnabled)
        assertFalse(ui.searchEnabled)
    }

    @Test
    fun buscandoRadarNoSeTocaDeNuevo() {
        val ui = chrome(searching = true)
        assertTrue(ui.showSearch)
        assertFalse(ui.searchEnabled)
    }

    @Test
    fun supuestosNoSePisan() {
        val cases = listOf(
            chrome(),
            chrome(parked = true),
            chrome(liveStatus = ParkingStatus.AVAILABLE, liveOwnerIsMe = true),
            chrome(liveStatus = ParkingStatus.CLAIMED, liveClaimantIsMe = true),
            chrome(liveStatus = ParkingStatus.CLAIMED, liveOwnerIsMe = true),
        )
        cases.forEach { ui ->
            assertFalse(ui.showAssignedCard && ui.showOwnerHandoff, "un usuario no es cedente y reclamante a la vez")
            assertFalse(ui.showParkedCard && ui.iAmYielding, "Lo dejaste acá no convive con la cesión")
            assertFalse(ui.showSearch && ui.iAmYielding, "el radar no corre mientras cedo")
            assertFalse(ui.showSearch && ui.assignedToMe, "el radar no corre con lugar asignado")
            assertEquals(ui.yieldSelected, ui.iAmYielding)
            assertFalse(ui.showNotifyingCopy)
            if (ui.iAmYielding && !ui.showOwnerHandoff) {
                assertFalse(ui.showMap)
                assertTrue(ui.showCancelYield)
            }
            assertEquals(ui.showAssignedCard, ui.assignedToMe)
            if (ui.assignedToMe) assertFalse(ui.showYield)
        }
    }
}
