//package com.local.passover.services
//
//import android.app.Notification
//import android.app.PendingIntent
//import android.app.Service
//import android.content.Intent
//import android.os.Handler
//import android.os.IBinder
//import android.os.Looper
//import android.provider.MediaStore
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import com.local.passover.MainActivity
//import com.local.passover.R
//import com.local.passover.bluetoothClassic.BluetoothL2capManager
//import com.local.passover.classes.AppRepository
//import com.local.passover.classes.NetworkManager
//import com.local.passover.classes.PacketManager
//import com.local.passover.clipboard.ClipboardActivity
//import com.local.passover.clipboard.ScreenshotObserver
//import dagger.hilt.android.AndroidEntryPoint
//import timber.log.Timber
//import javax.inject.Inject
//import kotlin.concurrent.timer
//
//
//@AndroidEntryPoint
//class BLEConnectionService:Service() {
//    private val BLE_SERVICE_TAG = "BLEConnectionService"
//    private var notificationCompatManager: NotificationManagerCompat? = null
//    private var isServiceRunning = false
//    private var screenshotObserver: ScreenshotObserver? = null
//    private val mainThreadHandler = Handler(Looper.getMainLooper())
//
//    @Inject
//    lateinit var repository: AppRepository
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        if (intent != null) {
//            val action = intent.action
//            Timber.tag(BLE_SERVICE_TAG).i("Received action: $action")
//            when(action){
//                ACTIONS.START.toString() -> onStart()
//                ACTIONS.STOP.toString() -> stopService()
//            }
//        }
//        return START_STICKY
//    }
//
//    private fun onStart() {
//        if(isServiceRunning){
//            return
//        }
//        PacketManager.setAppContext(application)
//        notificationCompatManager = NotificationManagerCompat.from(applicationContext)
//        val notification = defaultNotification()
//        startForeground(1, notification)
//        isServiceRunning = true
//        repository.setServiceRunning(isServiceRunning)
//        BluetoothL2capManager.startServer(applicationContext)
//        NetworkManager.startServer(applicationContext)
//        setupScreenshotObserver()
//    }
//
//    override fun onDestroy() {
//        Timber.tag(BLE_SERVICE_TAG).i("Destroying service")
//        stopService()
//        super.onDestroy()
//        isServiceRunning = false
//        repository.setServiceRunning(isServiceRunning)
//    }
//
//    private fun stopService(){
//        NetworkManager.stopServer()
//        BluetoothL2capManager.closeConnection()
//
//        if (screenshotObserver != null) {
//            contentResolver.unregisterContentObserver(screenshotObserver!!)
//            screenshotObserver = null // Clear the reference
//            Timber.tag(BLE_SERVICE_TAG).i("ScreenshotObserver unregistered.")
//        }
//
////        TODO: check this
//        val stopAppIntent = Intent("com.local.passover.STOP_APP")
//        sendBroadcast(stopAppIntent)
//        stopForeground(STOP_FOREGROUND_REMOVE)
//        stopSelf()
//    }
//
//
//
//
//    private fun defaultNotification():Notification{
//        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
//        val openAppPendingIntent = PendingIntent.getActivity(applicationContext, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
//
////        PendingIntent to launch clipboard transparent Activity
//        val readIntent = Intent(this, ClipboardActivity::class.java).apply {
//            addFlags(
//                Intent.FLAG_ACTIVITY_NEW_TASK or
//                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
//                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
//            )
//        }
//
//        val readPending = PendingIntent.getActivity(
//            this,
//            0,
//            readIntent,
//            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//        )
//
//
//        return NotificationCompat.Builder(applicationContext, "passover_channel")
//            .setSmallIcon(R.drawable.ic_notification)
//            .setContentTitle("Passover")
//            .setContentText("Sync clipboard data")
//            .setOngoing(true)
//            .setContentIntent(openAppPendingIntent)
//            .addAction(
//                android.R.drawable.ic_menu_view,
//                "Read clipboard",
//                readPending
//            )
//            .build()
//    }
//
//    private fun setupScreenshotObserver() {
//        Timber.tag(BLE_SERVICE_TAG).i("Setting up screenshot observer")
//        screenshotObserver = ScreenshotObserver(this, mainThreadHandler) { uri ->
//            // This code block will be executed when a screenshot is detected
//            Timber.tag(BLE_SERVICE_TAG).i("Screenshot detected! URI: $uri")
//        }
//
//        // Register the observer to listen for changes to images
//        contentResolver.registerContentObserver(
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            true, // Notify for descendants of the URI
//            screenshotObserver!!
//        )
//    }
//
//
//
//    enum class ACTIONS {
//        START,
//        STOP,
//        UPDATE
//    }
//}
