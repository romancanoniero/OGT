package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class CrmUserListingTest {
    @Test fun readAccessRequiresOperationalRole() {
        for (role in listOf("OWNER", "OPERATIONS", "TRUST_SAFETY", "SUPPORT")) {
            assertTrue(crmCanViewUserListing(role))
        }
        for (role in listOf("COMPANY_ADMIN", "USER", "ANALYST", "FINANCE", null)) {
            assertFalse(crmCanViewUserListing(role))
        }
    }

    @Test fun enumsDatesAndFiltersAreStrictlyValidated() {
        val filters = crmParseUserListFilters(mapOf(
            "statuses" to listOf("ACTIVE", "SUSPENDED", "ACTIVE"),
            "roles" to listOf("USER"),
            "from" to "2026-10-01",
            "to" to "2026-10-25",
        ))
        assertEquals(listOf("ACTIVE", "SUSPENDED"), filters?.statuses)
        assertEquals(listOf("USER"), filters?.roles)
        assertNull(crmParseUserListFilters(mapOf("statuses" to listOf("root"))))
        assertNull(crmParseUserListFilters(mapOf("roles" to listOf("OWNER"))))
        assertNull(crmParseUserListFilters(mapOf("from" to "2026-15-25")))
        assertNull(crmParseUserListFilters(mapOf("from" to "2026-10-25", "to" to "2026-10-01")))
        assertEquals(CrmUserListFilters(), crmParseUserListFilters(null))
    }

    @Test fun orderAndCursorRemainStableAcrossPages() {
        val orders = crmParseUserListOrders(listOf(
            mapOf("by" to "status", "direction" to "asc"),
            mapOf("by" to "name", "direction" to "desc"),
        ))!!
        assertEquals(listOf("text", "text", "date", "uuid"), crmUserSortKeys(orders).map { it.kind })
        assertEquals(listOf("asc", "desc", "desc", "asc"), crmUserSortKeys(orders).map { it.direction })
        assertNull(crmParseUserListOrders(listOf(mapOf("by" to "email", "direction" to "asc"))))
        assertNull(crmParseUserListOrders(listOf(mapOf("by" to "name", "direction" to "random"))))
        assertNull(crmParseUserListOrders(listOf(
            mapOf("by" to "name", "direction" to "asc"),
            mapOf("by" to "name", "direction" to "desc"),
        )))
        val cursor = CrmUserListCursor("Ana", CrmUserListFilters(statuses = listOf("ACTIVE")), orders,
            listOf("ACTIVE", "ana", "2026-10-25T10:00:00.123456Z", UUID.randomUUID().toString()))
        assertEquals(cursor, crmParseUserListCursor(crmEncodeUserListCursor(cursor)))
        assertNull(crmParseUserListCursor("u1.invalid"))
        assertNull(crmParseUserListCursor("v3.invalid"))
    }

    @Test fun theUsersListDoesNotExposeContactDataOrBuildMutableSql() {
        assertTrue(CRM_USERS_LIST_BASE.contains("FROM users u"))
        assertTrue(CRM_USERS_LIST_BASE.contains("last_seen_at"))
        assertFalse(CRM_USERS_LIST_BASE.contains("email"))
        assertFalse(CRM_USERS_LIST_BASE.contains("phone"))
        assertFalse(CRM_USERS_LIST_BASE.contains("firebase_uid"))
        assertFalse(CRM_USERS_LIST_BASE.contains("DELETE"))
        assertTrue(crmUserSortKeys(emptyList()).last().kind == "uuid")
    }
}
