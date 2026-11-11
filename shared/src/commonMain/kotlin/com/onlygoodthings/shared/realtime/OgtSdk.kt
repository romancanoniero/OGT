package com.onlygoodthings.shared.realtime

import com.iyr.db.core.DbApp
import com.iyr.db.core.DbOptions
import com.iyr.db.database.DbDatabase

/**
 * Boot del cliente de sockets propio (db-kmp-sdk).
 * La UI solo necesita llamar [start] con el JWT de Firebase.
 *
 * El SDK en SHA 0a881b7 expone `tokenProvider: suspend () -> String?` y
 * `persistenceEnabled`. No hay `persistenceDir` ni `forceReconnect`.
 */
object OgtSdk {
    private var active = false
    private val _generation = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val generation: kotlinx.coroutines.flow.StateFlow<Long> get() = _generation

    /** Replace only the app-owned connection when its authenticated transport changes. */
    fun restart(gatewayEndpoint: String, tokenProvider: suspend (Boolean) -> String?): DbDatabase {
        shutdown()
        DbApp.initialize(DbOptions(endpoint=gatewayEndpoint,tokenProvider={ tokenProvider(false) },persistenceEnabled=false))
        val database=DbDatabase.getInstance()
        active = true
        _generation.value++
        return database
    }
    fun start(
        gatewayEndpoint: String,
        tokenProvider: suspend (forceRefresh: Boolean) -> String?,
        persistenceDir: String? = null,
    ): DbDatabase = startZeroArg(
        gatewayEndpoint = gatewayEndpoint,
        // El SDK fijado pide el token sin bandera. El refresh queda en el proveedor de la app.
        tokenProvider = { tokenProvider(false) },
        persistenceDir = persistenceDir,
    )

    /** Proveedor sin bandera de refresh. Lo usa la fachada JS (`{ token }`). */
    fun start(
        gatewayEndpoint: String,
        tokenProvider: suspend () -> String?,
        persistenceDir: String? = null,
    ): DbDatabase = startZeroArg(gatewayEndpoint, tokenProvider, persistenceDir)

    private fun startZeroArg(
        gatewayEndpoint: String,
        tokenProvider: suspend () -> String?,
        persistenceDir: String?,
    ): DbDatabase {
        if (!DbApp.isInitialized()) {
            DbApp.initialize(
                DbOptions(
                    endpoint = gatewayEndpoint,
                    tokenProvider = tokenProvider,
                    persistenceEnabled = persistenceDir != null,
                ),
            )
        }
        active = true
        return DbDatabase.getInstance()
    }

    fun database(): DbDatabase {
        check(active) { "OGT realtime no está conectado" }
        return DbDatabase.getInstance()
    }

    fun isStarted(): Boolean = active && runCatching { DbApp.isInitialized() }.getOrDefault(false)

    /** El SDK actual no tiene forceReconnect; goOnline reabre el socket si está caído. */
    fun reconnect() {
        if (isStarted()) {
            DbDatabase.getInstance().goOnline()
        }
    }

    fun shutdown() {
        val wasActive = active
        active = false
        if (wasActive && DbApp.isInitialized()) {
            DbDatabase.getInstance().close()
            _generation.value++
        }
    }
}
