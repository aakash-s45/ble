package com.example.bleexample.services

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.bleexample.models.AppRepository
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
            "BLE Connect",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }
}