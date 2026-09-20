package com.onlygoodthings.app.map

import platform.Foundation.NSLock

private val lock = NSLock()

internal actual fun <T> withTileCacheLock(block: () -> T): T {
    lock.lock()
    return try {
        block()
    } finally {
        lock.unlock()
    }
}
