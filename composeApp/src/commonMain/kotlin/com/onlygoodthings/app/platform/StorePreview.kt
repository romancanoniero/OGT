package com.onlygoodthings.app.platform

import com.onlygoodthings.app.nav.OgtRoute

/** Capturas de ficha: entra al feed con el perfil de preview. */
expect fun isStorePreviewLaunch(): Boolean

/** Pantalla inicial de ficha: `feed`, `parking`, `animals`, `skills`, `publish`, `map`, `petsmap`. */
expect fun storePreviewScreenName(): String?

fun storePreviewRoute(): OgtRoute = when (storePreviewScreenName()?.lowercase()) {
    "parking" -> OgtRoute.Parking
    "animals", "pets" -> OgtRoute.Animals
    "skills" -> OgtRoute.Skills
    "publish", "homenaje", "ternura" -> OgtRoute.Publish
    "adopt", "adopcion", "lost", "perdido" -> OgtRoute.ReportAnimal
    "map" -> OgtRoute.Map
    "petsmap" -> OgtRoute.PetsMap
    else -> OgtRoute.Feed
}
