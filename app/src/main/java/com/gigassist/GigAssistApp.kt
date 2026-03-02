package com.gigassist

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Clase Application de GigAssist.
 * Inicializa Hilt y Timber al arrancar la app.
 */
@HiltAndroidApp
class GigAssistApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar Timber solo en debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
