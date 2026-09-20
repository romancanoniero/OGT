package com.onlygoodthings.shared.data

/** API en la VPS. HTTP de lab, como la demo de Dentastic. */
const val OGT_DEFAULT_API_BASE = "http://217.216.82.209:19080"

class SessionStore {
    var firebaseJwt: String? = null
    var apiBaseUrl: String = OGT_DEFAULT_API_BASE
    var socketUrl: String = "ws://217.216.82.209:19080/ws"
}
