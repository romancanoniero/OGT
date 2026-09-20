package com.onlygoodthings.app.map

/** Abre Google Maps en navegación a pie hacia el destino. */
expect fun openWalkingDirections(latitude: Double, longitude: Double)

fun walkingDirectionsWebUrl(latitude: Double, longitude: Double): String =
    "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=walking"
