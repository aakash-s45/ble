package com.local.passover.services
import android.app.Notification
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.passover.clipboard.ClipboardHandler
import com.local.passover.core.ConnectionRepository
import com.local.passover.core.ServiceRepository
import com.local.passover.network.WebSocketClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainService: LifecycleService() {
    private val TAG = "MainService"
    @Inject lateinit var serviceRepo: ServiceRepository
    @Inject lateinit var connectionRepo: ConnectionRepository
    @Inject lateinit var clipboardHandler: ClipboardHandler
    @Inject lateinit var webSocketClient: WebSocketClient

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())

        lifecycleScope.launch {
//            TODO: fetch credentials and do test connection
//            val creds = serviceRepo.getCredentials()
//            if(creds != null){
//                connectionRepo.makeTestConnection(creds.deviceId, creds.key)
//            }
        }

        lifecycleScope.launch {
            webSocketClient.messages.collect {
                Timber.tag(TAG).i("Received message: $it")
            }
        }
    }

    private fun createNotification(): Notification{
        return NotificationCompat
            .Builder(this, "passover_channel")
            .setContentTitle("App is running")
            .build()
    }
}