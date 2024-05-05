package com.example.bleexample.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.example.bleexample.R
import com.example.bleexample.models.MediaDataStore
import com.example.bleexample.models.NewServer


class BLEConnectionService:Service() {
    private var notificationManager: NotificationManager? = null
    private var isServiceRunning = false
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            Log.i("BLEService", action.toString())
            when(action){
                ACTIONS.START.toString() -> onStart()
                ACTIONS.STOP.toString() -> NewServer.stop()
                ACTIONS.UPDATE.toString() -> {
                    Log.i("BLEService", MediaDataStore.mediaState.title)
//                    TODO: update notification here
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        NewServer.stop()
        super.onDestroy()
        isServiceRunning = false
    }

    fun foregroundServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (BLEConnectionService::class.java.getName() == service.service.className) {
                return true
            }
        }
        return false
    }

    fun onStart() {
        MediaDataStore.setAppContext(application)
        if(isServiceRunning){
            return
        }
//        val notification: Notification = NotificationCompat.Builder(applicationContext,"ble_sync_channel")
//            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Yo").build()
        val notification = showMediaControlNotification("temp", isPlaying = false)
        if (notificationManager == null) {
            startForeground(1, notification)
        }
        else{
            notificationManager?.notify(1, notification)
        }
        isServiceRunning = true
        NewServer.start(application)
    }



    enum class ACTIONS {
        START,
        STOP,
        UPDATE
    }


    fun showMediaControlNotification(metadata: String, isPlaying: Boolean): Notification {
        val context = applicationContext
        val notificationBuilder = NotificationCompat.Builder(context, "ble_sync_channel")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("Media Title")
            .setContentText(metadata)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(null) // Use your MediaSession token if available
                    .setShowActionsInCompactView(0) // Index of the play/pause action
            )
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.Blue.toArgb()) // Set your desired color
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        // Add play/pause action based on the current state
        val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
        notificationBuilder.addAction(android.R.drawable.ic_media_next, "Previous", null) // Add previous action
            .addAction(R.drawable.play, "Play", null) // Add play action
            .addAction(R.drawable.bluetooth_outlines, "Next", null)
//        val playPauseAction = NotificationCompat.Action.Builder(
//            playPauseIcon,
//            "Play/Pause",
//            MediaControlReceiver.ACTION_PLAY_PAUSE
//        ).build()
//        notificationBuilder.addAction(playPauseAction)

        // Show the notification
        return notificationBuilder.build()
    }

    object MediaControlReceiver {
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        // Define more actions as needed
    }

}
