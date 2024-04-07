package com.example.bleexample.models

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.bleexample.utils.SCAN_PERIOD
import com.example.bleexample.utils.myServiceUUID1

const val TAG = "BLEViewModel"

class BLEScanViewModel (private val bluetoothAdapter:BluetoothAdapter): ViewModel(){
    private var scanner:BluetoothLeScanner? = null
    private lateinit var  scanFilter :List<ScanFilter>
    private lateinit var scanSettings: ScanSettings

    var scanResults: MutableMap<String, BluetoothDevice> = mutableMapOf()
        private set
    var scanningForDevice = mutableStateOf(false)
        private set
    var isScanFailed = mutableStateOf(false)
        private set
    var selectedDevice by mutableStateOf<BluetoothDevice?>(null)
        private set
    var textValue by mutableStateOf("")
        private set
    var isDeviceConnected = mutableStateOf(false)
        private set
    var messages = mutableStateOf(mutableListOf<String>())
        private set

    private val handler = Handler(Looper.getMainLooper())

    fun updateTextValue(newValue:String){
        textValue = newValue
    }

    fun addNewMessage(message:String){
        messages.value.add(message)
    }
    fun setConnectionState(state:Boolean){
        Log.d("BleSCANViewModel","connection state changed to $state")
        isDeviceConnected.value = state
    }


    @SuppressLint("MissingPermission")
    fun startScan(){
        if(bluetoothAdapter.isEnabled){
            scanFilter = buildScanFilters()
            scanSettings = buildScanSettings()
            scanner = bluetoothAdapter.bluetoothLeScanner
            scanningForDevice.value = true
            handler.postDelayed({stopScanning()}, SCAN_PERIOD.toLong())
            Log.i(TAG,"Scanning for devices")
            scanner?.startScan(scanFilter,scanSettings,scanCallback)
        }
        else{
            Log.i(TAG,"Bluetooth is not enabled")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning(){
        if(scanningForDevice.value){
            scanner?.stopScan(scanCallback)
            scanningForDevice.value = false
            Log.i(TAG,"Stopped scanning")
        }
    }

    private val scanCallback = object :ScanCallback(){
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (item in results) {
                item.device?.let { device ->
                    scanResults[device.address] = device
                }
            }
            scanResults.forEach {
                Log.d(TAG,"onBatchScanResults: ${it.key}")
            }
        }

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            super.onScanResult(callbackType, result)
            result.device?.let { device ->
                scanResults[device.address] = device
            }
            scanResults.forEach {
                Log.d(TAG,"onScanResult: ${it.key}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanFailed.value = true
            // Send error state to the fragment to display
            val errorMessage = "Scan failed with error: $errorCode"
            Log.e(TAG, errorMessage)
        }
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val scanFilters: MutableList<ScanFilter> = ArrayList()
        val builder = ScanFilter.Builder()
        builder.setServiceUuid(ParcelUuid.fromString(myServiceUUID1.toString()))
        scanFilters.add(builder.build())
        return scanFilters
    }
    private fun buildScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun selectDevice(device: BluetoothDevice){
        Log.d(TAG,"Selected Device: ${device.address}")
        selectedDevice = device
        L2CAPServer.getScannedDevice(device)
    }

}