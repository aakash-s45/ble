package com.local.passover.services

import android.app.Application
import com.local.passover.utils.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class PassoverApplication : Application(){
    override fun onCreate() {

        Timber.plant(Timber.DebugTree())
        Timber.plant(FileLoggingTree(this))
        Timber.i("Application created and logging is ready.")

        super.onCreate()
    }
}