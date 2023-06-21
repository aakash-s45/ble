package com.example.bleexample

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.bleexample.models.BLEScanViewModel
import com.example.bleexample.screens.Home
import com.example.bleexample.services.BLEService
import com.example.bleexample.ui.theme.BLEExampleTheme
import com.example.bleexample.utils.enableLocation
import com.example.bleexample.utils.isLocationEnabled

const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private var bleService:BLEService? = null
    lateinit var viewModel:BLEScanViewModel
    lateinit var bluetoothAdapter:BluetoothAdapter

    private val serviceConnection = object :ServiceConnection{
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BLEService.LocalBinder
            bleService = binder.getService()
            bleService?.let{ service ->
                if(!service.init()){
                    Log.e(TAG,"Unable to initialize Bluetooth")
                    finish()
                }
                if(viewModel.selectedDevice!=null){
                    Log.i(TAG,"Connecting to device")
                    service.startServer()
                    service.connectToDevice(viewModel.selectedDevice!!)
                }
                else{
                    Log.e(TAG,"No device selected")
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null

        }
    }
    private val gattUpdateReceiver = object:BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){
                BLEService.ACTION_GATT_CONNECTED->{
                    Log.i(TAG,"Connected to GATT server")
                }
                BLEService.ACTION_GATT_DISCONNECTED->{
                    Log.i(TAG,"Disconnected from GATT server")
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gattServiceIntent = Intent(this,BLEService::class.java)
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
                        Button(onClick = {
                            if(viewModel.selectedDevice!=null){
                                bindService(gattServiceIntent,serviceConnection,Context.BIND_AUTO_CREATE)
                            }

                        }){
                            Text("BindService")
                        }
                        Button(onClick = {unbindService(serviceConnection)}){
                            Text("UnbindService")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
    }
    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
        unregisterReceiver(gattUpdateReceiver)
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
    private fun makeGattUpdateIntentFilter():IntentFilter?{
        return IntentFilter().apply {
            addAction(BLEService.ACTION_GATT_CONNECTED)
            addAction(BLEService.ACTION_GATT_DISCONNECTED)
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
