package com.onlygoodthings.shared.realtime

import com.iyr.db.core.DbApp
import com.iyr.db.core.DbOptions
import com.iyr.db.database.DbDatabase

/**
 * Boot del cliente de sockets propio (db-kmp-sdk).
 * La UI solo necesita llamar [start] con el JWT de Firebase.
 *
 * El SDK pide `tokenProvider: suspend (forceRefresh) -> String?`.
 * `true` tiene que llegar al proveedor: si no, un token vencido no se renueva.
 */
object OgtSdk {
    fun start(
        gatewayEndpoint: String,
        tokenProvider: suspend (forceRefresh: Boolean) -> String?,
        persistenceDir: String? = null,
    ): DbDatabase = open(gatewayEndpoint, tokenProvider, persistenceDir)

    /** Proveedor sin bandera de refresh. Lo usa la fachada JS (`{ token }`). */
    fun start(
        gatewayEndpoint: String,
        tokenProvider: suspend () -> String?,
        persistenceDir: String? = null,
    ): DbDatabase = open(gatewayEndpoint, { _ -> tokenProvider() }, persistenceDir)

    private fun open(
        gatewayEndpoint: String,
        tokenProvider: suspend (forceRefresh: Boolean) -> String?,
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
        return DbDatabase.getInstance()
    }

    fun database(): DbDatabase = DbDatabase.getInstance()

    fun isStarted(): Boolean = runCatching { DbApp.isInitialized() }.getOrDefault(false)

    /** El SDK actual no tiene forceReconnect; goOnline reabre el socket si está caído. */
    fun reconnect() {
        if (DbApp.isInitialized()) {
            DbDatabase.getInstance().goOnline()
        }
    }

    fun shutdown() {
        if (DbApp.isInitialized()) {
            DbDatabase.getInstance().close()
        }
    }
}
