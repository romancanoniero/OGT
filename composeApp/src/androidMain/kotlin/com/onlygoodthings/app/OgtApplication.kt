package com.onlygoodthings.app

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.lang.ref.WeakReference

class OgtApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
        // Emuladores: sin SafetyNet/Play Integrity. El SMS real se reemplaza por números de prueba.
        FirebaseAuth.getInstance().firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
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
