package com.onlygoodthings.backend.auth

/** Unsafe authentication modes require an explicit, non-production environment. */
data class AuthSettings(val allowDevTokens: Boolean, val emulatorHost: String?) {
    companion object {
        fun from(env: (String) -> String?): AuthSettings {
            val environment = env("OGT_ENV") ?: "production"
            require(environment in setOf("production", "development", "test")) { "OGT_ENV inválido" }
            val allowDev = env("OGT_ALLOW_DEV_TOKENS")?.toBooleanStrictOrNull() ?: false
            val emulator = env("FIREBASE_AUTH_EMULATOR_HOST")?.takeIf { it.isNotBlank() }
            require(environment != "production" || (!allowDev && emulator == null)) {
                "Producción no admite tokens de laboratorio ni el emulador Firebase"
            }
            if (emulator != null) {
                require(Regex("^(localhost|127\\.0\\.0\\.1):[0-9]{1,5}$").matches(emulator)) { "El emulador Firebase debe ser local" }
                require(env("OGT_FIREBASE_PROJECT_ID")?.startsWith("demo-") == true) { "El emulador requiere un proyecto demo-" }
            }
            return AuthSettings(allowDev, emulator)
        }
    }
}
