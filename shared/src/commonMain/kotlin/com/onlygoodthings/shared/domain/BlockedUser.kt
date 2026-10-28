package com.onlygoodthings.shared.domain
@kotlinx.serialization.Serializable
data class BlockedUser(val userId:String,val displayName:String,val createdAtEpochMs:Long)
