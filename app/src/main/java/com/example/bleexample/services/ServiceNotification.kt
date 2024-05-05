package com.example.bleexample.services

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class ServiceNotification : Application(){
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