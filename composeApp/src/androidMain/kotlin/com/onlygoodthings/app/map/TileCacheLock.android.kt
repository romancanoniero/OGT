package com.onlygoodthings.app.map

private val lock = Any()

internal actual fun <T> withTileCacheLock(block: () -> T): T = synchronized(lock, block)
