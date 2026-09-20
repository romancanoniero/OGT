package com.onlygoodthings.app.map

import platform.AudioToolbox.AudioServicesPlaySystemSound

actual fun playParkingFoundSound() {
    // 1111 = chime corto de “nuevo ítem”; no es el tono genérico de teclado.
    AudioServicesPlaySystemSound(1111u)
}
