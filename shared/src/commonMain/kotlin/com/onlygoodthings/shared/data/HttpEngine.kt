package com.onlygoodthings.shared.data

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(): HttpClient

/** Cliente liviano para teselas: sin logs y con timeout corto. */
expect fun createTileHttpClient(): HttpClient
