package com.fuelqueue

import android.app.Application
import com.fuelqueue.utils.SessionManager

class FuelQueueApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(applicationContext)
    }
}