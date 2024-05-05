package com.example.bleexample.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bleexample.R
import com.example.bleexample.models.MediaDataStore
import com.example.bleexample.models.NewServer


class BLEConnectionService:Service() {
    private var notificationCompatManager: NotificationManagerCompat? = null
    private var mediaSessionCompat: MediaSessionCompat? = null
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
                    updateNotification()
//                    onStart()
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


    fun onStart() {
        MediaDataStore.setAppContext(application)
        if(isServiceRunning){
            return
        }
        notificationCompatManager = NotificationManagerCompat.from(applicationContext)
        mediaSessionCompat = MediaSessionCompat(applicationContext, "tag")
        val notification = showNotification()
        if (notificationCompatManager == null) {
            startForeground(1, notification)
        }
        else{
            notificationCompatManager?.notify(1, notification)
        }
        isServiceRunning = true
        NewServer.start(application)
    }

    private fun updateNotification(){
        val notification = showNotification()
        notificationCompatManager?.notify(1,notification)
    }

    private fun showNotification(): Notification {
        val image: Bitmap? = MediaDataStore.mediaState.artwork
        val title = MediaDataStore.mediaState.title
        val artist = MediaDataStore.mediaState.artist

//        val playImage = R.drawable.play
//        val pauseImage = R.drawable.pause
//        val nextImage = R.drawable.next
//        var previousImage = R.drawable.prev

        val playImage = android.R.drawable.ic_media_play
        val pauseImage = android.R.drawable.ic_media_pause
        val nextImage = android.R.drawable.ic_media_next
        val previousImage = android.R.drawable.ic_media_previous


        return NotificationCompat.Builder(applicationContext, "ble_sync_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setOnlyAlertOnce(true)
            .setColor(resources.getColor(R.color.purple_200))
            .setLargeIcon(image)
            .setCustomBigContentView(null)
            .addAction(previousImage, "Previous", null) // Add previous action
            .addAction(playImage, "Play", null) // Add play action
            .addAction(nextImage, "Next", null) // Add next action
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSessionCompat!!.sessionToken)
            )
            .build()
    }



    enum class ACTIONS {
        START,
        STOP,
        UPDATE
    }
}
