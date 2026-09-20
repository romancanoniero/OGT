package com.onlygoodthings.shared.realtime

import com.iyr.db.core.DbApp
import com.iyr.db.core.DbOptions
import com.iyr.db.database.DbDatabase

/**
 * Boot del cliente de sockets propio (db-kmp-sdk).
 * La UI solo necesita llamar [start] con el JWT de Firebase.
 */
object OgtSdk {
    fun start(
        gatewayEndpoint: String,
        tokenProvider: suspend (forceRefresh: Boolean) -> String?,
        persistenceDir: String? = null,
    ): DbDatabase {
        if (!DbApp.isInitialized()) {
            DbApp.initialize(
                DbOptions(
                    endpoint = gatewayEndpoint,
                    tokenProvider = tokenProvider,
                    persistenceEnabled = persistenceDir != null,
                    persistenceDir = persistenceDir,
                ),
            )
        }
        return DbDatabase.getInstance()
    }

    fun database(): DbDatabase = DbDatabase.getInstance()

    fun isStarted(): Boolean = runCatching { DbApp.isInitialized() }.getOrDefault(false)

        fun reconnect() {
        if (DbApp.isInitialized()) {
            DbDatabase.getInstance().forceReconnect()
        }
    }

    fun shutdown() {
        if (DbApp.isInitialized()) {
            DbDatabase.getInstance().close()
        }
    }
}
