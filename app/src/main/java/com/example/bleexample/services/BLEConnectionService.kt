package com.example.bleexample.services

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.getBroadcast
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
import com.example.bleexample.MainActivity
import com.example.bleexample.R
import com.example.bleexample.models.MediaDataStore
import com.example.bleexample.models.NewServer
import com.example.bleexample.receivers.BLEConnectionReceiver


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
                ACTIONS.STOP.toString() -> {
                    NewServer.stop()
                    stopSelf()
                }
                ACTIONS.UPDATE.toString() -> {
                    Log.i("BLEService", MediaDataStore.mediaState.title)
//                    TODO: update notification here
                    updateNotification()
                }

            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        NewServer.notifyWithResponse("DESTROY")
        NewServer.stop()
        super.onDestroy()
        isServiceRunning = false
    }

    private fun updatePlaybackState(isPlaying: Boolean, totalDuration: Long, elapsedTime: Long) {
        Log.i("updatePlaybackState", "$isPlaying, $totalDuration, $elapsedTime")
        val mediaState = MediaDataStore.mediaState
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                elapsedTime,
                1f
            )
        mediaSessionCompat?.setPlaybackState(playbackStateBuilder.build())

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaState.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaState.artist)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaState.artwork)
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

        val playIntent = Intent(applicationContext, BLEConnectionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "PLAY"
        }

        val playPendingIntent = getBroadcast(
            applicationContext,
            0,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(applicationContext, BLEConnectionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "PAUSE"
        }

        val pausePendingIntent = getBroadcast(
            applicationContext,
            0,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(applicationContext, BLEConnectionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "NEXT"
        }

        val nextPendingIntent = getBroadcast(
            applicationContext,
            0,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(applicationContext, BLEConnectionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "PREVIOUS"
        }

        val previousPendingIntent = getBroadcast(
            applicationContext,
            0,
            previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(applicationContext, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        updatePlaybackState(
            isPlaying = MediaDataStore.mediaState.playbackRate,
            totalDuration = MediaDataStore.mediaState.duration.toLong()*1000,
            elapsedTime = MediaDataStore.mediaState.elapsed.toLong()*1000,
        )



        mediaSessionCompat?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                sendBroadcast(playIntent)
                Log.i("MediaSessionIntents", "onPlay")
            }

            override fun onPause() {
                sendBroadcast(pauseIntent)
                Log.i("MediaSessionIntents", "onPause")
            }

            override fun onSkipToNext() {
                sendBroadcast(nextIntent)
                Log.i("MediaSessionIntents", "onSkipToNext")
            }

            override fun onSkipToPrevious() {
                sendBroadcast(previousIntent)
                Log.i("MediaSessionIntents", "onSkipToPrevious")
            }
        })

        val nextImage = R.drawable.ic_media_next
        var previousImage = R.drawable.ic_media_previous

        val playPausePendingIntent = if(MediaDataStore.mediaState.playbackRate){
            pausePendingIntent
        }
        else{
            playPendingIntent
        }

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
            .addAction(previousImage, "Previous", previousPendingIntent) // Add previous action
            .addAction(centerAction, "Play", playPausePendingIntent) // Add play action
            .addAction(nextImage, "Next", nextPendingIntent) // Add next action
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSessionCompat!!.sessionToken)
            )
            .setContentIntent(openAppPendingIntent)
            .build()
    }



    enum class ACTIONS {
        START,
        STOP,
        UPDATE
    }
}
