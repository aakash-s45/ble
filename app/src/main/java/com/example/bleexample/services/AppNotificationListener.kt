package com.example.bleexample.services

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.example.bleexample.Message
import com.example.bleexample.models.AppRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var repository: AppRepository

    // Check if the Bluetooth service is running
    private fun isBluetoothServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (BLEConnectionService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isBluetoothServiceRunning()){
            return
        }
        val notification: Notification = sbn.notification
        val extras = notification.extras
        Log.d("NotificationListener34", "Bluetooth service is running")
        processNotificationV2(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isBluetoothServiceRunning()){
            return
        }
        // Handle notification removal if needed
        Log.i("NotificationListener", "removed noti: ${sbn.notification.toString()}")
    }


    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!isBluetoothServiceRunning()){
            return
        }
    }

    private fun processNotificationV2(sbn: StatusBarNotification) {

        val packageName = sbn.packageName
        Log.d("NotificationListener", "Notification posted from: $packageName")

        val notification: Notification = sbn.notification
        val extras = notification.extras
        val channelId = notification.channelId
        if(channelId=="ble_sync_channel"){
            return
        }

        if (notification.category == Notification.CATEGORY_TRANSPORT){
            // Convert SpannableString to String if necessary
            Log.d("NotificationListener1", "notification1: ${notification.extras.toString()}")
            Log.d("NotificationListener1", "notification2: ${notification.actions.toString()}")
            Log.d("NotificationListener1", "notification3: ${notification.bubbleMetadata.toString()}")
            Log.d("NotificationListener1", "notification4: ${notification.allowSystemGeneratedContextualActions}")

//            Log.d("NotificationListener1", "notification5: ${}") // app icon


//            Log.d("NotificationListener1", "notification6: ${notification.hasImage()}")
            Log.d("NotificationListener1", "notification6: ${notification.tickerText}")

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)

            val titleString = title?.toString() ?: "Unknown Title"
            val artistString = artist?.toString() ?: "Unknown Artist"

            val actions = notification.actions
            var isPlaying = false

            actions?.forEach { action ->
                val actionTitle = action.title.toString().lowercase()
                Log.d("NotificationListener1", "title: $actionTitle")
                if (actionTitle.contains("pause")) {
                    isPlaying = true
                    Log.d("NotificationListener", "Media is playing")
                } else if (actionTitle.contains("play")) {
                    isPlaying = false
                    Log.d("NotificationListener", "Media is paused")
                }
            }

            notification.getLargeIcon()

            Log.d("NotificationListener23", titleString)

            val mediaData =  Message.MediaData.newBuilder()
                .setTitle(titleString)
                .setArtist(artistString)
                .setAlbum("")
                .setBundle(packageName)
                .setElapsed(0.0)
                .setDuration(180.0)
                .setPlaybackRate(isPlaying)
                .build()

            repository.setMediaData(mediaData, "Phone")

            notification.getLargeIcon()?.loadDrawable(this)
                ?.toBitmap()?.let { repository.updateMediaDataByKey("artwork", it) }


        }
    }
}
