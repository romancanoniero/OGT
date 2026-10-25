package com.onlygoodthings.backend.crm

import com.onlygoodthings.shared.protocol.WsCodec
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/** Vista operativa de solo lectura. Nunca devuelve correo, teléfono o Firebase UID. */
@Serializable
internal data class CrmUserListItem(
    val id: String,
    val displayName: String,
    val status: String,
    val role: String,
    val createdAt: String,
    val lastSeenAt: String? = null,
)

@Serializable
internal data class CrmUserListResponse(
    val users: List<CrmUserListItem>,
    val nextCursor: String? = null,
)

@Serializable
internal data class CrmUserListFilters(
    val statuses: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val from: String = "",
    val to: String = "",
)

@Serializable
internal data class CrmUserListCursor(
    val query: String,
    val filters: CrmUserListFilters,
    val orders: List<CrmPostOrder>,
    val values: List<String>,
)

internal fun crmCanViewUserListing(role: String?): Boolean =
    role in setOf("OWNER", "OPERATIONS", "SUPPORT", "TRUST_SAFETY")

internal val crmUserStatuses = setOf("PENDING", "ACTIVE", "SUSPENDED", "DELETED")
internal val crmUserRoles = setOf("USER", "COMPANY_ADMIN", "COMMUNITY_MODERATOR")
internal val crmUserSortFields = setOf("createdAt", "name", "status", "role", "lastSeenAt")

internal fun crmParseUserListFilters(raw: Any?): CrmUserListFilters? {
    if (raw == null) return CrmUserListFilters()
    val obj = raw as? Map<*, *> ?: return null
    fun enumList(name: String, allowed: Set<String>): List<String>? {
        val values = obj[name] ?: return emptyList()
        if (values !is List<*> || values.size > allowed.size || values.any { it !is String || it !in allowed })
            return null
        return values.map { it as String }.distinct().sorted()
    }
    val statuses = enumList("statuses", crmUserStatuses) ?: return null
    val roles = enumList("roles", crmUserRoles) ?: return null
    val from = (obj["from"] ?: "") as? String ?: return null
    val to = (obj["to"] ?: "") as? String ?: return null
    if (from.isNotEmpty() && runCatching { LocalDate.parse(from) }.isFailure) return null
    if (to.isNotEmpty() && runCatching { LocalDate.parse(to) }.isFailure) return null
    if (from.isNotEmpty() && to.isNotEmpty() && from > to) return null
    return CrmUserListFilters(statuses, roles, from, to)
}

internal fun crmParseUserListOrders(raw: Any?): List<CrmPostOrder>? {
    if (raw == null) return listOf(CrmPostOrder("createdAt", "desc"))
    val values = raw as? List<*> ?: return null
    if (values.size > 4) return null
    val list = values.map {
        val obj = it as? Map<*, *> ?: return null
        val by = obj["by"] as? String ?: return null
        val direction = obj["direction"] as? String ?: return null
        if (by !in crmUserSortFields || direction !in setOf("asc", "desc")) return null
        CrmPostOrder(by, direction)
    }
    return list.takeIf { it.map(CrmPostOrder::by).distinct().size == it.size }
}

internal data class CrmUserSortKey(val sql: String, val direction: String, val kind: String, val column: String)

internal fun crmUserSortKeys(orders: List<CrmPostOrder>): List<CrmUserSortKey> {
    val keys = orders.map {
        when (it.by) {
            "name" -> CrmUserSortKey("lower(base.display_name)", it.direction, "text", "name_key")
            "status" -> CrmUserSortKey("base.status", it.direction, "text", "status")
            "role" -> CrmUserSortKey("base.role", it.direction, "text", "role")
            "lastSeenAt" -> CrmUserSortKey("base.seen_key", it.direction, "date", "seen_key")
            else -> CrmUserSortKey("base.created_at", it.direction, "date", "created_at")
        }
    }.toMutableList()
    if (orders.none { it.by == "createdAt" }) keys.add(CrmUserSortKey("base.created_at", "desc", "date", "created_at"))
    keys.add(CrmUserSortKey("base.id::uuid", "asc", "uuid", "id"))
    return keys
}

internal fun crmEncodeUserListCursor(value: CrmUserListCursor): String {
    val json = WsCodec.json.encodeToString(CrmUserListCursor.serializer(), value)
    return "u1." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.toByteArray(Charsets.UTF_8))
}

internal fun crmParseUserListCursor(raw: String): CrmUserListCursor? {
    if (!raw.startsWith("u1.") || raw.length !in 4..8192) return null
    val parsed = runCatching {
        WsCodec.json.decodeFromString(CrmUserListCursor.serializer(),
            String(Base64.getUrlDecoder().decode(raw.substring(3)), Charsets.UTF_8))
    }.getOrNull() ?: return null
    if (parsed.query.length > 120) return null
    if (parsed.filters != crmParseUserListFilters(mapOf(
        "statuses" to parsed.filters.statuses, "roles" to parsed.filters.roles,
        "from" to parsed.filters.from, "to" to parsed.filters.to,
    ))) return null
    if (parsed.orders != crmParseUserListOrders(parsed.orders.map {
        mapOf("by" to it.by, "direction" to it.direction)
    })) return null
    val keys = crmUserSortKeys(parsed.orders)
    if (parsed.values.size != keys.size || parsed.values.any { it.length > 200 }) return null
    if (keys.indices.any { i ->
        (keys[i].kind == "uuid" && runCatching { UUID.fromString(parsed.values[i]) }.isFailure) ||
        (keys[i].kind == "date" && runCatching { Instant.parse(parsed.values[i]) }.isFailure)
    }) return null
    return parsed
}

/** Todos los criterios se aplican ANTES del LIMIT; ningún campo SQL viene del cliente. */
internal const val CRM_USERS_LIST_BASE = """
SELECT u.id::text AS id, u.display_name, u.status::text AS status,
       u.role::text AS role, u.created_at, u.last_seen_at,
       COALESCE(u.last_seen_at, '1970-01-01 00:00:00+00'::timestamptz) AS seen_key
FROM users u
"""

internal fun crmSearchUsersPage(
    conn: Connection,
    query: String,
    filters: CrmUserListFilters,
    orders: List<CrmPostOrder>,
    cursor: CrmUserListCursor?,
): CrmUserListResponse {
    val keys = crmUserSortKeys(orders)
    val conditions = mutableListOf<String>()
    val args = mutableListOf<Pair<String, String>>()
    if (query.isNotEmpty()) {
        conditions.add("(base.display_name ILIKE ? ESCAPE '\\' OR base.id = ?)")
        args.add("text" to crmLikeContains(query))
        args.add("text" to query)
    }
    if (filters.statuses.isNotEmpty()) {
        conditions.add("(" + filters.statuses.joinToString(" OR ") { "base.status = ?" } + ")")
        filters.statuses.forEach { args.add("text" to it) }
    }
    if (filters.roles.isNotEmpty()) {
        conditions.add("(" + filters.roles.joinToString(" OR ") { "base.role = ?" } + ")")
        filters.roles.forEach { args.add("text" to it) }
    }
    val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    if (filters.from.isNotEmpty()) {
        conditions.add("base.created_at >= ?::timestamptz")
        args.add("date" to LocalDate.parse(filters.from).atStartOfDay(zone).toInstant().toString())
    }
    if (filters.to.isNotEmpty()) {
        conditions.add("base.created_at < ?::timestamptz")
        args.add("date" to LocalDate.parse(filters.to).plusDays(1).atStartOfDay(zone).toInstant().toString())
    }
    if (cursor != null) {
        val compare = keys.indices.map { n ->
            val checks = (0 until n).map { i ->
                "${keys[i].sql} = ?${if (keys[i].kind == "uuid") "::uuid" else if (keys[i].kind == "date") "::timestamptz" else ""}"
            }.toMutableList()
            checks.add("${keys[n].sql} ${if (keys[n].direction == "asc") ">" else "<"} ?${if (keys[n].kind == "uuid") "::uuid" else if (keys[n].kind == "date") "::timestamptz" else ""}")
            "(" + checks.joinToString(" AND ") + ")"
        }
        conditions.add("(" + compare.joinToString(" OR ") + ")")
        keys.indices.forEach { n -> for (i in 0..n) args.add(keys[i].kind to cursor.values[i]) }
    }
    val sql = buildString {
        append("SELECT base.*, lower(base.display_name) AS name_key FROM (")
        append(CRM_USERS_LIST_BASE.trim())
        append(") base")
        if (conditions.isNotEmpty()) append(" WHERE " + conditions.joinToString(" AND "))
        append(" ORDER BY " + keys.joinToString(", ") { "${it.sql} ${it.direction}" })
        append(" LIMIT 21")
    }
    val records = conn.prepareStatement(sql).use { stmt ->
        var index = 1
        args.forEach { (kind, value) ->
            if (kind == "date") stmt.setTimestamp(index++, Timestamp.from(Instant.parse(value)))
            else stmt.setString(index++, value)
        }
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    val item = CrmUserListItem(
                        id = rs.getString("id"),
                        displayName = rs.getString("display_name") ?: "Sin nombre",
                        status = rs.getString("status"),
                        role = rs.getString("role"),
                        createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                        lastSeenAt = rs.getTimestamp("last_seen_at")?.toInstant()?.toString(),
                    )
                    val vals = keys.map { key ->
                        when (key.kind) {
                            "uuid" -> item.id
                            "date" -> rs.getTimestamp(key.column).toInstant().toString()
                            else -> rs.getString(key.column)
                        }
                    }
                    add(item to vals)
                }
            }
        }
    }
    val nextCursor = if (records.size > 20)
        crmEncodeUserListCursor(CrmUserListCursor(query, filters, orders, records[19].second)) else null
    return CrmUserListResponse(records.take(20).map { it.first }, nextCursor)
}
