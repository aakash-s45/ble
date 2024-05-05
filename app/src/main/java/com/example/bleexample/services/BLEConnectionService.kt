package com.example.bleexample.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bleexample.R
import com.example.bleexample.models.MediaDataStore
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
            val action = intent.action
            Log.i("BLEService", action.toString())
            when(action){
                ACTIONS.START.toString() -> onStart()
                ACTIONS.STOP.toString() -> NewServer.stop()
                ACTIONS.UPDATE.toString() -> {
                    Log.i("BLEService", MediaDataStore.mediaState.title)
//                    TODO: update notification here
//                    onStart()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        NewServer.stop()
        super.onDestroy()
    }

    fun onStart() {
        val notificationCompatManager : NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)
        val mediaSessionCompat = MediaSessionCompat(applicationContext, "tag")

        val image : Bitmap = BitmapFactory.decodeResource(resources, R.drawable.placeholder_albumart)
        val title = "Title"
        val artist = "Artist"

        val playImage =  R.drawable.play
        val pauseImage = R.drawable.pause
        val nextImage = R.drawable.next
        var previousImage = R.drawable.prev


        val notification = NotificationCompat.Builder(applicationContext, "ble_sync_channel")
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
                    .setMediaSession(mediaSessionCompat.sessionToken)
            )
            .build()

        if (notificationCompatManager == null) {
            startForeground(1, notification)
        }
        else{
            notificationCompatManager?.notify(1, notification)
        }
        NewServer.start(application)
        MediaDataStore.setAppContext(application)
    }



    enum class ACTIONS {
        START,
        STOP,
        UPDATE
    }

}