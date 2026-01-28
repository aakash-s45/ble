package com.local.passover.services
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.passover.R
import com.local.passover.core.ClipboardHandler
import com.local.passover.core.ConnectionRepository
import com.local.passover.core.ConnectionState
import com.local.passover.network.WebSocketClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainService: LifecycleService() {
    private val TAG = "MainService"
    private val CHANNEL_ID = "passover_channel"
    @Inject lateinit var connectionRepo: ConnectionRepository
    @Inject lateinit var clipboardHandler: ClipboardHandler
    @Inject lateinit var webSocketClient: WebSocketClient

    private var reconnectionJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())

        lifecycleScope.launch {
            connectionRepo.hasCredentials.collect { hasCreds ->
                if(hasCreds){
                    Timber.tag(TAG).d("Credentials found, connecting")
                    connectionRepo.connectWithSavedCredentials()
                } else{
                    Timber.tag(TAG).d("No credentials found, waiting for connection")
                    connectionRepo.closeConnection()
                }
            }
        }

        lifecycleScope.launch {
            connectionRepo.connectionState.collect { state ->
                if(state == ConnectionState.FAILED){
                    Timber.tag(TAG).d("Connection failed, scheduling reconnect")
                    scheduleReconnection()
                }else{
                    reconnectionJob?.cancel()
                }
            }
        }

        lifecycleScope.launch {
            connectionRepo.incomingMessages.collect { message ->
                if(message.hasClipboard()){
                    clipboardHandler.updateClipboard(message.clipboard)
                }
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            connectionRepo.closeConnection()
        }
    }

    private fun scheduleReconnection(){
        if(reconnectionJob?.isActive == true)return
        reconnectionJob = lifecycleScope.launch {
            Timber.d("Scheduling reconnect")
//            TODO: make this delay exponential
            delay(5000)
            if(connectionRepo.hasCredentials.first()){
                connectionRepo.connectWithSavedCredentials()
            }
        }
    }

    private fun createNotification(): Notification{
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Passover Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("App is running")
            .setContentText("Keeping device in sync")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}