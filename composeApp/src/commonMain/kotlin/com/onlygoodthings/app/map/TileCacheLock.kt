package com.onlygoodthings.app.map

/** Lock de teselas: JVM usa monitor, Native usa NSLock. */
internal expect fun <T> withTileCacheLock(block: () -> T): T
