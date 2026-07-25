package com.juanpablo0612.sickreshapex

import android.app.Application
import com.juanpablo0612.sickreshapex.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SickReshapeXApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@SickReshapeXApplication)
            modules(appModule)
        }
    }
}
