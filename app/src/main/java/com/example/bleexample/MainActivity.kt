package com.example.bleexample

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import com.example.bleexample.models.MediaDataStore
import com.example.bleexample.models.MediaViewModel
import com.example.bleexample.models.PacketManager
import com.example.bleexample.models.RC
import com.example.bleexample.screens.MediaPage
import com.example.bleexample.services.BLEConnectionService
import com.example.bleexample.ui.theme.BLEExampleTheme
import com.example.bleexample.utils.askPermissions
import com.example.bleexample.utils.requiredPermissionsInitialClient

const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    lateinit var bluetoothAdapter:BluetoothAdapter
    private val mediaViewModel by viewModels<MediaViewModel>()

    private val multiplePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                Log.i(com.example.bleexample.screens.TAG, "Launcher result: $permissions")
                if (permissions.containsValue(false)) {
                    Log.i(com.example.bleexample.screens.TAG, "At least one of the permissions was not granted.")
                    Toast.makeText(
                        this,
                        "At least one of the permissions was not granted. Please do so manually",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //do something
                    Log.d(TAG, "onCreate: all permissions granted")
                }
            }

    override fun onStart() {
        Log.i("onStart", "starting")
        val intent = Intent(applicationContext, BLEConnectionService::class.java)
        intent.action = BLEConnectionService.ACTIONS.START.toString()
        applicationContext.startService(intent)
        MediaDataStore.setViewModel(mediaViewModel)
        mediaViewModel.updateState(MediaDataStore.mediaState)
        super.onStart()
    }
    override fun onStop() {
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> PacketManager.sendRemotePacket(RC.VOL_INC)
            KeyEvent.KEYCODE_VOLUME_DOWN -> PacketManager.sendRemotePacket(RC.VOL_DEC)
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askPermissions(multiplePermissionLauncher, requiredPermissionsInitialClient,this){
            Log.d(TAG, "onCreate: permissions granted")
        }
        val bluetoothManger = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManger.adapter

        if(!bluetoothAdapter.isEnabled){
            enableBluetooth()
        }
//        TODO: block to ask for location permission
//        if(!isLocationEnabled(this) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
//            Toast.makeText(this, "Location is required for this app to run", Toast.LENGTH_SHORT).show()
//            enableLocation(this)
//        }

//        check and ask for notification permission
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(this, "Notifications are required for this app to run", Toast.LENGTH_SHORT).show()
            NotificationManagerCompat.from(this).apply {
                val intent = Intent().apply {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("android.provider.extra.APP_PACKAGE", packageName)
                }
                startActivity(intent)
            }
        }

        // Register for broadcasts when a device is discovered
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(mReceiver, filter)

        setContent {
            BLEExampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediaPage(activity = this)
//                    Column (
//                        verticalArrangement = Arrangement.Center,
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                            )
//                    {
//                        Greeting("Android")
//                        Button(onClick = {
//                    TODO: use this function later to send message
//                            NewServer.sendMessage("button")
//                        }) {
//                            Text(text = "The button")
//                        }
//
////                        Home(viewModel = viewModel)
//                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
    }
    //    enable bluetooth pop up activation
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

    private fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothResultLauncher.launch(enableBtIntent)
    }


}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
