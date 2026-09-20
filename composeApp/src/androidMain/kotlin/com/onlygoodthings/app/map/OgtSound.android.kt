package com.onlygoodthings.app.map

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

actual fun playParkingFoundSound() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 280)
            }
        }, 170)
    }
}
