package com.onlygoodthings.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.onlygoodthings.app.auth.AuthPrefs
import com.onlygoodthings.app.map.OgtBackgroundLocation
import java.lang.ref.WeakReference

class OgtApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
        // Emuladores: sin SafetyNet/Play Integrity. El SMS real se reemplaza por números de prueba.
        FirebaseAuth.getInstance().firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        if (AuthPrefs().gpsEnabled && hasForegroundLocation()) {
            runCatching { OgtBackgroundLocation.start() }
        }
    }

    private fun hasForegroundLocation(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        lateinit var instance: OgtApplication
            private set
    }
}

object AndroidAuthHost {
    private var activityRef: WeakReference<ComponentActivity>? = null

    fun attach(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
    }

    fun requireActivity(): ComponentActivity =
        activityRef?.get() ?: error("No hay Activity activa para autenticar")
}

val Context.app: OgtApplication get() = applicationContext as OgtApplication
