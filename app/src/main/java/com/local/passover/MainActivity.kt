package com.local.passover

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.local.passover.screens.Home
import com.local.passover.ui.theme.BLEExampleTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import kotlin.system.exitProcess


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private  val TAG = "MainActivity"
//    private val appViewModel by viewModels<AppViewModel>()
//    @Inject lateinit var repository:AppRepository
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        unregisterReceiver(stopAppReceiver)
    }

    override fun onStop() {
        super.onStop()
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stopAppIntent = IntentFilter("com.passover.STOP_APP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            registerReceiver(stopAppReceiver, stopAppIntent, RECEIVER_EXPORTED)
        }
        else{
            registerReceiver(stopAppReceiver, stopAppIntent)
        }

        setContent {
            BLEExampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Home(activity = this)
                }

            }
        }
    }

    private val stopAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.local.passover.STOP_APP") {
                finishAffinity()
                exitProcess(0)
            }
        }
    }
}
