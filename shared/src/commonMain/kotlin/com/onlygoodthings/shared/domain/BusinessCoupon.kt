package com.onlygoodthings.shared.domain

@kotlinx.serialization.Serializable
data class BusinessCoupon(val id:String,val campaignId:String,val companyId:String,val companyName:String,val code:String,
    val status:String,val discountBps:Int,val issuedToUser:String?=null,val expiresAtEpochMs:Long,val redeemedAtEpochMs:Long?=null)
