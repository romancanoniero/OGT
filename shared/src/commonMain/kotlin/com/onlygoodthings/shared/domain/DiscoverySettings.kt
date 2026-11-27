package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable data class DiscoverySettings(val adoptionRadiusKm:Int=20,val version:Long=1)
@Serializable data class FeedDiscoveryScope(
    val latitude:Double?=null,val longitude:Double?=null,
    val settings:DiscoverySettings=DiscoverySettings(),val place:String?=null,
)
