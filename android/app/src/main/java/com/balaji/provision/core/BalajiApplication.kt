package com.balaji.provision.core

import android.app.Application
import org.maplibre.android.MapLibre
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BalajiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre keeps process-wide state (native library loading, thread pools) and
        // documents that getInstance() runs exactly once before any MapView is inflated.
        MapLibre.getInstance(this)
    }
}
