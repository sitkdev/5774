package com.trid.test.kmpsample

import android.app.Application
import com.trid.test.kmpsample.di.initKoin
import org.koin.android.ext.koin.androidContext

/**
 * Application entry point. Initializes Koin once per process, feeding the
 * application [Context] into the DI graph via `androidContext(...)`.
 */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MainApplication)
        }
    }
}
