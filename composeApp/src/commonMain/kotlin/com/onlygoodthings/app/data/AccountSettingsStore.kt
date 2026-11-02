package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.AccountSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class AccountSettingsStore(private val read: () -> String, private val write: (String) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun accounts(): Map<String, AccountSettings> =
        if (read().isBlank()) emptyMap() else json.decodeFromString(read())
    fun load(owner: String): AccountSettings = if (owner.isBlank()) AccountSettings() else accounts()[owner] ?: AccountSettings()
    fun save(owner: String, settings: AccountSettings) {
        if (owner.isNotBlank()) write(json.encodeToString(accounts() + (owner to settings)))
    }
    fun remove(owner: String) {
        if (owner.isNotBlank()) write(json.encodeToString(accounts() - owner))
    }
}
