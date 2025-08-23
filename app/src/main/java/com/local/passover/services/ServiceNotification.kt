package com.local.passover.services

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.local.passover.classes.AppRepository
import com.local.passover.utils.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ServiceNotification : Application(){

    @Inject
    lateinit var repository: AppRepository
    override fun onCreate() {

        Timber.plant(Timber.DebugTree())
        Timber.plant(FileLoggingTree(this))
        Timber.i("Application created and logging is ready.")

        super.onCreate()
        val channel = NotificationChannel(
            "ble_sync_channel",
            "Passover",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}