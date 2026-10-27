package com.onlygoodthings.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeScrollTest {
    @Test
    fun staysHiddenThroughoutDragAndInertiaThenRevealsOnScrollEnd() = runBlocking {
        val host = OgtChromeHost()
        host.present(Any(), OgtChromeSpec("Ajustes"))
        val connection = host.nestedScrollConnection()
        connection.onPostScroll(Offset(0f, -20f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(host.scrolling)
        // Una pausa mayor al antiguo temporizador no significa que el gesto haya terminado.
        delay(350)
        assertTrue(host.scrolling)
        connection.onPostScroll(Offset(0f, -50f), Offset.Zero, NestedScrollSource.SideEffect)
        assertTrue(host.scrolling)
        connection.onPostFling(Velocity(0f, -500f), Velocity.Zero)
        assertFalse(host.scrolling)
    }

    @Test
    fun dragWithoutInertiaAlsoRevealsOnRelease() = runBlocking {
        val host = OgtChromeHost()
        host.present(Any(), OgtChromeSpec("Ajustes"))
        val connection = host.nestedScrollConnection()
        connection.onPostScroll(Offset(0f, 20f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(host.scrolling)
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
        assertFalse(host.scrolling)
    }

    @Test
    fun layoutAdjustmentsSmallMovementsAndFixedHeadersKeepTheirBehavior() {
        val host = OgtChromeHost()
        val token = Any()
        host.present(token, OgtChromeSpec("Ajustes"))
        val connection = host.nestedScrollConnection()
        connection.onPostScroll(Offset(0f, 50f), Offset.Zero, NestedScrollSource.SideEffect)
        connection.onPostScroll(Offset(0f, 2f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(host.scrolling)
        host.present(token, OgtChromeSpec("Fijo", hideOnScroll = false))
        connection.onPostScroll(Offset(0f, 50f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(host.scrolling)
    }
}
