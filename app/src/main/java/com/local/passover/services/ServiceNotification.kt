package com.local.passover.services

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.local.passover.classes.AppRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ServiceNotification : Application(){

    @Inject
    lateinit var repository: AppRepository
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "ble_sync_channel",
            "Passover",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}