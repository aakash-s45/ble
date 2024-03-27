package com.example.bleexample.models

import android.annotation.SuppressLint
import android.app.Application
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
import android.os.ParcelUuid
import android.util.Log
import com.example.bleexample.utils.myCharacteristicsUUID1
import com.example.bleexample.utils.myCharacteristicsUUID2
import com.example.bleexample.utils.myCharacteristicsUUID3
import com.example.bleexample.utils.myServiceUUID1
import java.util.Timer
import java.util.TimerTask


object ChatServer{
    private var app: Application? = null
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var viewModel: BLEScanViewModel

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertiseSettings: AdvertiseSettings = buildAdvertiseSettings()
    private var advertiseData: AdvertiseData = buildAdvertiseData()

    private var gattServer: BluetoothGattServer? = null
    private var gattServerCallback: BluetoothGattServerCallback? = null

    private var gattClient: BluetoothGatt? = null
    private var gattClientCallback: BluetoothGattCallback? = null

    private var currentDevice: BluetoothDevice? = null

    private var gatt: BluetoothGatt? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null

    private fun getBluetoothAdapter(app:Application): BluetoothAdapter {
        this.app = app
        bluetoothManager = app.getSystemService(BluetoothManager::class.java)
        return bluetoothManager.adapter
    }
    fun startServer(app:Application,viewModel: BLEScanViewModel){
        bluetoothAdapter = getBluetoothAdapter(app)
        this.viewModel = viewModel
        if(bluetoothAdapter.isEnabled){
            setupGattServer(app)
            startAdvertisement()
        }
        else{
            Log.e("ChatServer","Bluetooth is not enabled")
        }
    }
    fun stopServer() {
        stopAdvertising()
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String): Boolean {
        Log.d(TAG, "Send a message")
        messageCharacteristic?.let { characteristic ->
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val messageBytes = message.toByteArray(Charsets.UTF_8)
            characteristic.value = messageBytes
            gatt?.let {
                val success = it.writeCharacteristic(messageCharacteristic)
                Log.d(TAG, "onServicesDiscovered: message send: $success")
                if (success) {
                    viewModel.addNewMessage(message)
                }
            } ?: run {
                Log.d(TAG, "sendMessage: no gatt connection to send a message with")
            }
        }
        return false
    }

    fun setCurrentChatConnection(device: BluetoothDevice) {
        currentDevice = device
        viewModel.setConnectionState(true)
        connectToChatDevice(device)
        Log.d("ChatServer", "Connected to : ${device.address}")
    }
    @SuppressLint("MissingPermission")
    private fun connectToChatDevice(device: BluetoothDevice) {
        gattClientCallback = GattClientCallback()
        gattClient = device.connectGatt(app, false, gattClientCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        Log.d(TAG, "Stopping Advertising with advertiser $advertiser")
        advertiser?.stopAdvertising(advertiseCallback)
        advertiseCallback = null
    }
    @SuppressLint("MissingPermission")
    private fun startAdvertisement() {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        Log.d(TAG, "startAdvertisement: with advertiser $advertiser")

        if (advertiseCallback == null) {
            advertiseCallback = DeviceAdvertiseCallback()
            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        }
    }
    @SuppressLint("MissingPermission")
    private fun setupGattServer(app: Application){
        gattServerCallback = GattServerCallback()
        gattServer = bluetoothManager.openGattServer(app,gattServerCallback).apply {
            addService(setupGattService())
        }
    }
    var tempCharacteristic = BluetoothGattCharacteristic(
        myCharacteristicsUUID3,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
    private fun setupGattService(): BluetoothGattService {
        // Setup gatt service
        val service = BluetoothGattService(myServiceUUID1, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        // need to ensure that the property is writable and has the write permission
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
//        temporary test characteristic

        service.addCharacteristic(tempCharacteristic)
        return service
    }
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
    private class GattServerCallback : BluetoothGattServerCallback() {
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
                Log.d("GattServer", "Server connected to ${device.address}")
                viewModel.setConnectionState(true)
                connectToChatDevice(device)
                startUpatingChar(device)

            } else {
                Log.d("GattServer", "Server disconnected from ${device.name}")
                viewModel.setConnectionState(false)
                currentDevice = null
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
            if (characteristic.uuid == myCharacteristicsUUID1) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                val message = value?.toString(Charsets.UTF_8)
                Log.d(TAG, "onCharacteristicWriteRequest: Have message: \"$message\"")
                message?.let {
                    viewModel.addNewMessage(it)
                    viewModel.updateTextValue(it)
                }
            }
        }
    }
    private class DeviceAdvertiseCallback : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            // Send error state to display
            val errorMessage = "Advertise failed with error: $errorCode"
            Log.d(TAG, errorMessage)
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising successfully started")
        }
    }
    private class GattClientCallback : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val isSuccess = status == BluetoothGatt.GATT_SUCCESS
            val isConnected = newState == BluetoothProfile.STATE_CONNECTED
            Log.d(TAG, "onConnectionStateChange: Client $gatt  success: $isSuccess connected: $isConnected")
            // try to send a message to the other device as a test
            if (isSuccess && isConnected) {
                viewModel.setConnectionState(true)
                // discover services
                gatt.discoverServices()
            }
            else{
                viewModel.setConnectionState(false)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(discoveredGatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(discoveredGatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered: Have gatt $discoveredGatt")
                gatt = discoveredGatt
                val service = discoveredGatt.getService(myServiceUUID1)
                if(service!=null){
                    messageCharacteristic = service.getCharacteristic(myCharacteristicsUUID1)
                }
            }
        }
    }


    private var updateTimer: Timer? = null
    private fun startUpatingChar(device:BluetoothDevice){
        var temp1 = 1
        Log.i(TAG, "Start updateing char")
        if (updateTimer == null) {
            updateTimer = Timer()
            updateTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    temp1+=1
                    writeCharValue(temp1.toString(), device)
                }
            }, 0, 1000) // Update every 1000ms (1 second)
        }
    }
    @SuppressLint("MissingPermission")
    private fun writeCharValue(value:String, device: BluetoothDevice){
        print("New value set $value")
        Log.i(TAG, "Start updateing char $value")
        tempCharacteristic.value = value.toByteArray()
        gattServer?.notifyCharacteristicChanged(device, tempCharacteristic, true)
    }
}