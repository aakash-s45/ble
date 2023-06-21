package com.example.bleexample.services

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.example.bleexample.utils.myCharacteristicsUUID1
import com.example.bleexample.utils.myCharacteristicsUUID2
import com.example.bleexample.utils.myServiceUUID1

const val TAG = "BLEService"

class BLEService : Service() {
    private val binder = LocalBinder()
    private var bluetoothAdapter:BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED
    private var gattService: BluetoothGattService? = null
    private var gattCharacteristic:BluetoothGattCharacteristic? = null
    private lateinit var bluetoothManager: BluetoothManager
    private var gattServer: BluetoothGattServer? = null


    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertiseSettings: AdvertiseSettings = buildAdvertiseSettings()
    private var advertiseData: AdvertiseData = buildAdvertiseData()

    inner class  LocalBinder: Binder(){
        fun getService():BLEService{
            return this@BLEService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun init():Boolean{
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if(bluetoothAdapter==null){
            Log.e(TAG,"Unable to obtain a BluetoothAdapter")
            return false
        }
        return true
    }

    fun startServer(){
        bluetoothAdapter?.let { adapter->
            if(adapter.isEnabled){
                setupGattServer()
//                startAdvertisement()
            }
            else{
                Log.e(TAG,"BluetoothAdapter is not enabled")
            }
        }
    }
    fun stopServer() {

        stopAdvertising()
    }


    @SuppressLint("MissingPermission")
    private fun startAdvertisement() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        Log.d(TAG, "startAdvertisement: with advertiser $advertiser")

        if (advertiseCallback == null) {
            advertiseCallback = DeviceAdvertiseCallback()

            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer(){
        gattServer = bluetoothManager.openGattServer(this,gattServerCallback).apply {
            addService(setupGattService())
        }
    }

    private fun setupGattService():BluetoothGattService{
        val service = BluetoothGattService(myServiceUUID1,BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val messageCharacteristic = BluetoothGattCharacteristic(
            myCharacteristicsUUID1,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageCharacteristic)
        val confirmCharacteristic = BluetoothGattCharacteristic(
            myCharacteristicsUUID2,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(confirmCharacteristic)
        return service
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice):Boolean{
        return try{
            bluetoothGatt = device.connectGatt(this,false,gattCallback)
            true
        }catch (e:IllegalArgumentException){
            Log.e(TAG,"Device not found")
            false
        }
    }

    private fun broadcastUpdate(action:String){
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String): Boolean {
        Log.d(TAG, "Send a message")
        gattCharacteristic?.let { characteristic ->
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val messageBytes = message.toByteArray(Charsets.UTF_8)
            characteristic.value = messageBytes
            bluetoothGatt?.let {
                val success = it.writeCharacteristic(gattCharacteristic)
                Log.d(TAG, "onServicesDiscovered: message send: $success")
                if (success) {
                    Log.d(TAG, "onServicesDiscovered: message send: $success")
                }
            } ?: run {
                Log.d(TAG, "sendMessage: no gatt connection to send a message with")
            }
        }
        return false
    }

    private val gattCallback = object : BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if(newState == BluetoothProfile.STATE_CONNECTED){
                Log.i(TAG,"Connected to GATT server")
                bluetoothGatt = gatt
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
                bluetoothGatt?.discoverServices()
            }
            else if(newState==BluetoothProfile.STATE_DISCONNECTED){
                Log.i(TAG,"Disconnected from GATT server")
                bluetoothGatt?.close()
                bluetoothGatt = null
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if(status==BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                gattService = bluetoothGatt?.getService(myServiceUUID1)
                gattCharacteristic = gattService?.getCharacteristic(myCharacteristicsUUID1)
                val message = "hello from android"

                gattCharacteristic?.let { characteristic ->
                    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
                        gatt.writeCharacteristic(characteristic,message.toByteArray(Charsets.UTF_8),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    }
                    else{
                        sendMessage(message)
                    }
                }
            }
        }
    }

    private val gattServerCallback  = object: BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            val isSuccess = status == BluetoothGatt.GATT_SUCCESS
            val isConnected = newState == BluetoothProfile.STATE_CONNECTED
            Log.d(
                TAG,
                "onConnectionStateChange: Server $device ${device.name} success: $isSuccess connected: $isConnected"
            )
            if (isSuccess && isConnected) {
                Log.d(TAG, "onConnectionStateChange: Server connected to device ${device.name}")
            } else {
                Log.d(TAG, "onConnectionStateChange: Server disconnected from device ${device.name}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            if (characteristic.uuid == myCharacteristicsUUID2) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                val message = value?.toString(Charsets.UTF_8)
                Log.d(TAG, "onCharacteristicWriteRequest: Have message: \"$message\"")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        Log.d(TAG, "Stopping Advertising with advertiser $advertiser")
        if(advertiseCallback!=null){
            advertiser?.stopAdvertising(advertiseCallback)
            advertiseCallback = null
        }
    }

    private class DeviceAdvertiseCallback : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            // Send error state to display
            val errorMessage = "Advertise failed with error: $errorCode"
            Log.d(TAG, "Advertising failed")
            //_viewState.value = DeviceScanViewState.Error(errorMessage)
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising successfully started")
        }
    }

    /**
     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
     */
    private fun buildAdvertiseData(): AdvertiseData {
        val dataBuilder = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(myServiceUUID1))
            .setIncludeDeviceName(true)
        return dataBuilder.build()
    }
    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            .build()
    }
    @SuppressLint("MissingPermission")
    private fun close(){
        stopServer()
        bluetoothGatt?.let { gatt->
            gatt.close()
            bluetoothGatt = null
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG,"Unbinding service")
        close()
        return super.onUnbind(intent)
    }

    companion object{
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTED = 2
        const val ACTION_GATT_CONNECTED = "com.example.bleexample.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.bleexample.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bleexample.ACTION_GATT_SERVICES_DISCOVERED"
    }
}