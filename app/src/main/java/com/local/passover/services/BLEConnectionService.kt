package com.local.passover.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.local.passover.MainActivity
import com.local.passover.R
import com.local.passover.classes.AppRepository
import com.local.passover.bluetoothClassic.NewServer
import com.local.passover.classes.PacketManager
import com.local.passover.clipboard.ClipboardActivity
import com.local.passover.clipboard.ScreenshotObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BLEConnectionService:Service() {
    private var notificationCompatManager: NotificationManagerCompat? = null
    private var isServiceRunning = false
    private var screenshotObserver: ScreenshotObserver? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    @Inject
    lateinit var repository: AppRepository

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            Log.i("BLEService", action.toString())
            when(action){
                ACTIONS.START.toString() -> onStart()
                ACTIONS.STOP.toString() -> stopService()
            }
        }
//        return super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun onStart() {
        if(isServiceRunning){
            return
        }
        PacketManager.setAppContext(application)
        notificationCompatManager = NotificationManagerCompat.from(applicationContext)
        val notification = defaultNotification()
        startForeground(1, notification)
        isServiceRunning = true
        NewServer.start(application)
        setupScreenshotObserver()
    }

    override fun onDestroy() {
        Log.i("BLEService", "Destroying service")
        stopService()
        super.onDestroy()
        isServiceRunning = false
    }

    private fun stopService(){
        NewServer.stop()

        if (screenshotObserver != null) {
            contentResolver.unregisterContentObserver(screenshotObserver!!)
            screenshotObserver = null // Clear the reference
            Log.i("BLEService", "ScreenshotObserver unregistered.")
        }

//        TODO: check this
        val stopAppIntent = Intent("com.local.passover.STOP_APP")
        sendBroadcast(stopAppIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }




    private fun defaultNotification():Notification{
        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(applicationContext, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

//        PendingIntent to launch clipboard transparent Activity
        val readIntent = Intent(this, ClipboardActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }

        val readPending = PendingIntent.getActivity(
            this,
            0,
            readIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


        return NotificationCompat.Builder(applicationContext, "ble_sync_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Passover")
            .setContentText("Sync clipboard data")
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Read clipboard",
                readPending
            )
            .build()
    }

    private fun setupScreenshotObserver() {
        Log.d("CCService", "Setup screenshot observer")
        screenshotObserver = ScreenshotObserver(this, mainThreadHandler) { uri ->
            // This code block will be executed when a screenshot is detected
            Log.d("CCService", "Screenshot detected! URI: $uri. Launching reader...")
        }

        // Register the observer to listen for changes to images
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true, // Notify for descendants of the URI
            screenshotObserver!!
        )
    }



    enum class ACTIONS {
        START,
        STOP,
        UPDATE
    }
}
