package com.example.bleexample.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.bleexample.R
import com.example.bleexample.models.MediaViewModel
import com.example.bleexample.models.NewServer

class BLEConnectionService:Service() {
    private var notificationManager: NotificationManager? = null
    private lateinit var viewModel: MediaViewModel
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            Log.i("BLEService", intent.action.toString())
        }
        onStart()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        NewServer.stop()
        super.onDestroy()
    }

    fun onStart() {

        val notification: Notification = NotificationCompat.Builder(applicationContext,"ble_sync_channel")
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Yo").build()
        if (notificationManager == null) {
            startForeground(1, notification)
        }
        else{
            notificationManager?.notify(1, notification)
        }
        NewServer.start(application)
    }

}