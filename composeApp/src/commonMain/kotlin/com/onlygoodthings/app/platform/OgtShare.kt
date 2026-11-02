package com.onlygoodthings.app.platform

/** Hoja nativa: WhatsApp, Telegram y el resto de apps del sistema. */
expect fun sharePlainText(text: String)
/** Opens the system save/share sheet for a private JSON file. Never sends it automatically. */
expect suspend fun shareAccountExport(json: String, isCurrentOwner: () -> Boolean)
