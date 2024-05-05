package com.example.bleexample.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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

    fun updatePlaybackState(isPlaying: Boolean, totalDuration: Long, elapsedTime: Long, title:String) {
        Log.i("updatePlaybackState", "$isPlaying, $totalDuration, $elapsedTime")
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO)
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                elapsedTime,
                1f
            )
         mediaSessionCompat?.setPlaybackState(playbackStateBuilder.build())

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, totalDuration)
        mediaSessionCompat?.setMetadata(metadataBuilder.build())
    }


    fun onStart() {
        MediaDataStore.setAppContext(application)
        if(isServiceRunning){
            return
        }
        notificationCompatManager = NotificationManagerCompat.from(applicationContext)
        mediaSessionCompat = MediaSessionCompat(applicationContext, "tag")
        val notification = showNotification()
        startForeground(1, notification)
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
        mediaSessionCompat?.isActive = MediaDataStore.mediaState.playbackRate

        updatePlaybackState(
            isPlaying = MediaDataStore.mediaState.playbackRate,
            totalDuration = MediaDataStore.mediaState.duration.toLong(),
            elapsedTime = MediaDataStore.mediaState.elapsed.toLong(),
            title = MediaDataStore.mediaState.title
        )


        val nextImage = R.drawable.ic_media_next
        var previousImage = R.drawable.ic_media_previous

        val centerAction =  if (MediaDataStore.mediaState.playbackRate){
            R.drawable.ic_media_pause
        }
        else{
            R.drawable.ic_media_play
        }



        return NotificationCompat.Builder(applicationContext, "ble_sync_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setOnlyAlertOnce(true)
            .setColor(resources.getColor(R.color.black))
            .setLargeIcon(image)
            .setCustomBigContentView(null)
            .addAction(previousImage, "Previous", null) // Add previous action
            .addAction(centerAction, "Play", null) // Add play action
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
