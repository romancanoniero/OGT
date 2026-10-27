package com.onlygoodthings.shared.data

import io.ktor.client.plugins.logging.Logger

/** URLs remain useful for diagnosis without exposing signed-media access or search parameters. */
fun redactHttpLog(message:String):String = Regex("(https?://[^\\s?]+)\\?[^\\s]+")
    .replace(message) { "${it.groupValues[1]}?[redacted]" }

object OgtHttpLogger:Logger {
    override fun log(message:String) { println("HttpClient: ${redactHttpLog(message)}") }
}
