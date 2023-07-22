package com.example.bleexample

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.bleexample.models.BLEScanViewModel
import com.example.bleexample.models.ChatServer
import com.example.bleexample.screens.Home
import com.example.bleexample.ui.theme.BLEExampleTheme
import com.example.bleexample.utils.askPermissions
import com.example.bleexample.utils.enableLocation
import com.example.bleexample.utils.isLocationEnabled
import com.example.bleexample.utils.requiredPermissionsInitialClient

const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    lateinit var viewModel:BLEScanViewModel
    lateinit var bluetoothAdapter:BluetoothAdapter

    val multiplePermissionLauncher =
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
        super.onStart()
        ChatServer.startServer(application,viewModel)
    }
    override fun onStop() {
        super.onStop()
        ChatServer.stopServer()
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
        if(!isLocationEnabled(this) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
            Toast.makeText(this, "Location is required for this app to run", Toast.LENGTH_SHORT).show()
            enableLocation(this)
        }
        viewModel = BLEScanViewModel(bluetoothAdapter)
        // Register for broadcasts when a device is discovered
        var filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(mReceiver, filter)

        setContent {
            BLEExampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column (
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                            )
                    {
                        Greeting("Android")
                        Home(viewModel = viewModel)
                    }
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
