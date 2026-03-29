package com.local.passover.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.passover.R
import com.local.passover.core.ClipboardHandler
import com.local.passover.core.SyncOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService() {
    private val TAG = "MainService"
    private val CHANNEL_ID = "passover_channel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_PAUSE = "com.local.passover.ACTION_PAUSE"
        const val ACTION_RESUME = "com.local.passover.ACTION_RESUME"
    }

    @Inject lateinit var syncOrchestrator: SyncOrchestrator
    @Inject lateinit var clipboardHandler: ClipboardHandler

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PAUSE -> {
                Log.d(TAG, "Received PAUSE action")
                syncOrchestrator.pause()
                updateNotification("Sync paused")
            }
            ACTION_RESUME -> {
                Log.d(TAG, "Received RESUME action")
                updateNotification("Keeping device in sync")
                syncOrchestrator.resume()
            }
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification("Keeping device in sync"))
        syncOrchestrator.start()

        lifecycleScope.launch {
            syncOrchestrator.clipboardEvents.collect { clipboard ->
                clipboardHandler.updateClipboard(clipboard)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncOrchestrator.closeConnection()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Passover Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Passover")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}
