package com.onlygoodthings.shared.domain

/** Discovery uses the chosen area or a current, explicitly supplied device fix. Never a saved home/GPS position. */
object PetDiscovery {
    fun requiresLocation(kind:String?,topic:String=""):Boolean =
        kind in setOf("ADOPTION","LOST","FOUND") || topic.trim().lowercase() in setOf("adopción","mascota perdida","mascota encontrada")

    fun reference(manual:GeoPoint?,device:GeoPoint?):GeoPoint? = manual ?: device

    fun device(allowed:Boolean,point:GeoPoint?,at:Long,now:Long):GeoPoint? =
        point?.takeIf { allowed && now-at in 0..120_000 }

    fun allows(own:Boolean,viewer:GeoPoint?,animal:GeoPoint?,radiusKm:Int=20):Boolean =
        AdoptionProximity.allows(own,viewer,animal,radiusKm)
}
