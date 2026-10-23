package com.amarmasraf.internet

import android.app.Application

class AmarMasrafApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsManager.initialize(this)
    }
}
