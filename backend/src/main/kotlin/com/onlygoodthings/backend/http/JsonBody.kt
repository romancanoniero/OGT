package com.onlygoodthings.backend.http

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText

/**
 * Parseo de POST alineado al contrato del equipo:
 * el body llega como String y se proyecta a Map<String, Any?>.
 */
object JsonBody {
    val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    fun parse(data: String): Map<String, Any?> {
        return objectMapper.readValue(data, object : TypeReference<Map<String, Any?>>() {})
    }

    suspend fun receiveMap(call: ApplicationCall): Map<String, Any?> = parse(call.receiveText())
}

fun Map<String, Any?>.reqString(key: String): String =
    this[key] as? String ?: error("Campo requerido: $key")

fun Map<String, Any?>.optString(key: String): String? = this[key] as? String

fun Map<String, Any?>.reqDouble(key: String): Double =
    (this[key] as? Number)?.toDouble() ?: error("Campo numérico requerido: $key")

fun Map<String, Any?>.reqInt(key: String): Int =
    (this[key] as? Number)?.toInt() ?: error("Campo entero requerido: $key")

fun Map<String, Any?>.optInt(key: String, default: Int): Int =
    (this[key] as? Number)?.toInt() ?: default

fun Map<String, Any?>.optDouble(key: String): Double? =
    (this[key] as? Number)?.toDouble()

fun Map<String, Any?>.optBoolean(key: String, default: Boolean = false): Boolean =
    optBooleanOrNull(key) ?: default

fun Map<String, Any?>.optBooleanOrNull(key: String): Boolean? =
    when (val value = this[key]) {
        is Boolean -> value
        is String -> when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> null
        }
        else -> null
    }
