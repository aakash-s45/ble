package com.local.passover

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.local.passover.classes.AppRepository
import com.local.passover.classes.AppViewModel
import com.local.passover.classes.PacketManager
import com.local.passover.screens.Home
import com.local.passover.ui.theme.BLEExampleTheme
import com.local.passover.utils.askPermissions
import com.local.passover.utils.requiredPermissionsInitialClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    lateinit var bluetoothAdapter:BluetoothAdapter
    private val appViewModel by viewModels<AppViewModel>()
    @Inject
    lateinit var repository:AppRepository

    private val multiplePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                Log.i(TAG, "Launcher result: $permissions")
                if (permissions.containsValue(false)) {
                    Log.i(TAG, "At least one of the permissions was not granted.")
                    Toast.makeText(
                        this,
                        "At least one of the permissions was not granted. Please do so manually",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.d(TAG, "onCreate: all permissions granted")
                }
            }

    override fun onStart() {
        Log.i("onStart", "starting")
//        val intent = Intent(applicationContext, BLEConnectionService::class.java)
//        intent.action = BLEConnectionService.ACTIONS.START.toString()
//        applicationContext.startService(intent)
        PacketManager.setViewModel(appViewModel)
        super.onStart()
    }
    override fun onStop() {
        Log.i(TAG, "Stopped main activity")
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

        askPermissions(multiplePermissionLauncher, requiredPermissionsInitialClient,this){
            Log.d(TAG, "onCreate: permissions granted")
        }
        val bluetoothManger = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManger.adapter

        if(!bluetoothAdapter.isEnabled){
            enableBluetooth()
        }

        // Register for broadcasts when a device is discovered
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(mReceiver, filter)

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
        unregisterReceiver(stopAppReceiver)
    }


    private val enableBluetoothResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth Enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for this app to run", Toast.LENGTH_SHORT)
                .show()
            this.finish()
        }
    }
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    //Since our app needs bluetooth to work correctly we don't let the user turn it off
                    if (bluetoothAdapter.state == BluetoothAdapter.STATE_OFF
                    ) {
                        enableBluetooth()
                    }
                }
            }
        }
    }

    private val stopAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.local.passover.STOP_APP") {
                finishAffinity() // Finish all activities
                exitProcess(0) // Terminate the app process
            }
        }
    }
    private fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothResultLauncher.launch(enableBtIntent)
    }
}
