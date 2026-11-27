package com.onlygoodthings.shared.domain

import kotlinx.datetime.*
import kotlinx.serialization.Serializable

val gatheringTopics=listOf("Huerta comunitaria","Merienda comunitaria","Reforestación","Limpieza de playa","Compost comunitario","Trueque")

@Serializable
data class GatheringPostDraft(
    val body:String,val topic:String,val place:String,val eventStartsAtEpochMs:Long,
    val media:List<PostMediaItem>,val requestId:String,val attributionUrl:String?=null,
    val publicationKind:String="CONVOCATORIA",
    val latitude:Double?=null, val longitude:Double?=null,
)

fun gatheringStart(day:String,clock:String,timeZone:TimeZone=TimeZone.currentSystemDefault()):Long {
    val date=Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").matchEntire(day.trim())
        ?: throw IllegalArgumentException("Ingresá el día como DD/MM/AAAA.")
    val time=Regex("^(\\d{2}):(\\d{2})$").matchEntire(clock.trim())
        ?: throw IllegalArgumentException("Ingresá la hora como HH:mm.")
    return try {
        LocalDateTime(date.groupValues[3].toInt(),date.groupValues[2].toInt(),date.groupValues[1].toInt(),time.groupValues[1].toInt(),time.groupValues[2].toInt()).toInstant(timeZone).toEpochMilliseconds()
    } catch(_:IllegalArgumentException) { throw IllegalArgumentException("Revisá el día y la hora de la convocatoria.") }
}

fun validateGathering(body:String,topic:String,place:String,start:Long,now:Long,allowPast:Boolean=false) {
    require(body.trim().isNotEmpty()) { "Contá qué actividad están organizando." }
    require(isGatheringPost(topic)) { "Elegí un tipo de convocatoria válido." }
    require(place.trim().length in 3..200) { "Ingresá el lugar o punto de encuentro (hasta 200 caracteres)." }
    require(start>0 && (allowPast || start>now)) { "Elegí una fecha y hora futuras." }
}
