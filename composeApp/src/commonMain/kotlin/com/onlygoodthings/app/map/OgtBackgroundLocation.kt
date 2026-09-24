package com.onlygoodthings.app.map

/** Servicio de proceso: sigue midiendo aunque la UI no esté en primer plano. */
expect object OgtBackgroundLocation {
    fun start()
    fun stop()
}
