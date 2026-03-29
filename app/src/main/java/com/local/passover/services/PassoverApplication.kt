package com.local.passover.services


import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PassoverApplication : Application(){
    override fun onCreate() {
        super.onCreate()
        Log.i("PassoverApplication", "Application created and logging is ready.")
    }
}
