package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationSyncPolicyTest {

    @Test
    fun primerFixSiempreSube() {
        assertTrue(
            LocationSyncPolicy.shouldUpload(null, null, 0L, -34.6, -58.4, 1_000L),
        )
    }

    @Test
    fun mismoSitioAntesDe45sNoSube() {
        assertFalse(
            LocationSyncPolicy.shouldUpload(
                -34.6037,
                -58.3816,
                1_000L,
                -34.6037,
                -58.3816,
                20_000L,
            ),
        )
    }

    @Test
    fun espera45sAunqueNoSeMueva() {
        assertTrue(
            LocationSyncPolicy.shouldUpload(
                -34.6037,
                -58.3816,
                1_000L,
                -34.6037,
                -58.3816,
                1_000L + LocationSyncPolicy.MIN_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun ochentaMetrosSubeYa() {
        // ~90 m al este de Obelisco.
        assertTrue(
            LocationSyncPolicy.shouldUpload(
                -34.6037,
                -58.3816,
                1_000L,
                -34.6037,
                -58.3806,
                2_000L,
            ),
        )
    }
}
