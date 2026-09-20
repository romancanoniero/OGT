package com.onlygoodthings.shared.realtime

fun currentEpochMs(): Long =
    kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

fun nextRequestId(): String =
    "req-${kotlin.random.Random.nextLong(0, Long.MAX_VALUE).toString(16)}"
