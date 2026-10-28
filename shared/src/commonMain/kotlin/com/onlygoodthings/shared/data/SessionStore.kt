package com.onlygoodthings.shared.data

/** Default production endpoints use TLS; laboratory endpoints are explicit overrides. */
const val OGT_DEFAULT_API_BASE = "https://onlygoodthings.lat"

class SessionStore {
    var firebaseJwt: String? = null
    var apiBaseUrl: String = OGT_DEFAULT_API_BASE
    var socketUrl: String = "wss://onlygoodthings.lat/db"
}
