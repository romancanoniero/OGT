package com.onlygoodthings.backend.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet

class Database(jdbcUrl: String, user: String, password: String) {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            maximumPoolSize = 12
            isAutoCommit = true
            addDataSourceProperty("reWriteBatchedInserts", "true")
        },
    )

    fun <T> withConnection(block: (Connection) -> T): T =
        dataSource.connection.use(block)
}

fun ResultSet.stringOrNull(column: String): String? = getString(column)

fun ResultSet.intOrNull(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

fun ResultSet.longOrNull(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

fun ResultSet.doubleOrNull(column: String): Double? {
    val value = getDouble(column)
    return if (wasNull()) null else value
}
