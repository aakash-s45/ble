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
import com.local.passover.core.ConnectionRepository
import com.local.passover.core.TrustedPeerStore
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
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_PAUSE = "com.local.passover.ACTION_PAUSE"
        const val ACTION_RESUME = "com.local.passover.ACTION_RESUME"
    }

    @Inject lateinit var connectionRepo: ConnectionRepository
    @Inject lateinit var clipboardHandler: ClipboardHandler
    @Inject lateinit var webSocketClient: WebSocketClient
    @Inject lateinit var trustedPeerStore: TrustedPeerStore

    private var reconnectionJob: Job? = null
    private var isPaused = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PAUSE -> {
                Timber.tag(TAG).d("Received PAUSE action")
                isPaused = true
                reconnectionJob?.cancel()
                connectionRepo.closeConnection()
                updateNotification("Sync paused")
            }
            ACTION_RESUME -> {
                Timber.tag(TAG).d("Received RESUME action")
                isPaused = false
                updateNotification("Keeping device in sync")
                lifecycleScope.launch {
                    try {
                        if (connectionRepo.hasTrustedPeers.first()) {
                            connectToFirstTrustedPeer()
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Resume connection failed")
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification("Keeping device in sync"))

        lifecycleScope.launch {
            connectionRepo.hasTrustedPeers.collect { hasPeers ->
                if (isPaused) return@collect
                if(hasPeers){
                    Timber.tag(TAG).d("Trusted peer found, connecting")
                    try { connectToFirstTrustedPeer() } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Connection attempt failed")
                    }
                } else{
                    Timber.tag(TAG).d("No trusted peers found, waiting for pairing")
                    connectionRepo.closeConnection()
                }
            }
        }

        lifecycleScope.launch {
            connectionRepo.connectionState.collect { state ->
                if (isPaused) return@collect
                when (state) {
                    ConnectionState.FAILED -> {
                        Timber.tag(TAG).d("Connection failed, scheduling reconnect")
                        scheduleReconnection()
                    }
                    ConnectionState.CONNECTED, ConnectionState.IDLE -> {
                        reconnectionJob?.cancel()
                    }
                    else -> { /* DISCOVERING / CONNECTING — let the reconnection job finish */ }
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
        connectionRepo.closeConnection()
    }

    private fun scheduleReconnection(){
        if (isPaused) return
        if(reconnectionJob?.isActive == true)return
        reconnectionJob = lifecycleScope.launch {
            Timber.tag(TAG).d("Scheduling reconnect in 5s")
            delay(5000)
            try {
                if(connectionRepo.hasTrustedPeers.first()){
                    connectToFirstTrustedPeer()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Reconnection attempt failed")
            }
        }
    }

    private suspend fun connectToFirstTrustedPeer() {
        val peers = trustedPeerStore.getAllPeers()
        val peer = peers.firstOrNull()
        if (peer != null) {
            connectionRepo.connectWithTrustedPeer(peer.deviceId)
        } else {
            Timber.tag(TAG).d("No trusted peers available to connect")
        }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification{
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