package com.example.bleexample.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.util.Log
import com.example.bleexample.models.MediaState
import com.example.bleexample.models.MediaViewModel
import com.example.bleexample.models.PacketManager
import com.example.bleexample.models.TAG
import com.example.bleexample.models.TG
import com.example.bleexample.models.toData
import com.example.bleexample.utils.myCharacteristicsUUID1
import com.example.bleexample.utils.myCharacteristicsUUID2
import com.example.bleexample.utils.myServiceUUID2

class BleServer(private val app: Application, private val bluetoothManager: BluetoothManager){
    private var bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var serversocket: BluetoothServerSocket? = null

    private var gattServer: BluetoothGattServer? = null
    private var gattServerCallback: BluetoothGattServerCallback? = null
    private var bleAdvertiser: BleAdvertiser? = null
    private var bleDevice: BluetoothDevice? = null

    private var mediaData: MediaState? = null
    private var viewModel: MediaViewModel? = null
    private var deviceName: String = ""


    @SuppressLint("MissingPermission")
    fun start(){
        gattServerCallback = GattServerCallback()
        gattServer = bluetoothManager.openGattServer(app, gattServerCallback
        ).apply {
            addService(setupGattService())
        }
        bleAdvertiser = BleAdvertiser(bluetoothAdapter).apply {
            start()
        }
        mediaData = MediaState()
    }

    @SuppressLint("MissingPermission")
    fun stop(){
        bleAdvertiser?.stop()
        serversocket?.close()
        gattServer?.apply {
            clearServices()
            close()
        }
    }

    fun setViewModel(mediaViewModel: MediaViewModel? = null){
        if (mediaViewModel != null) {
            Log.i("123BLEServer", "Setting view-model")
            this.viewModel = mediaViewModel
        }
        else{
            Log.i("123BLEServer", "No view-model")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String){
        if (bleDevice != null){
            psmCharacteristic.value = message.toByteArray()
            gattServer?.notifyCharacteristicChanged(bleDevice, psmCharacteristic,true)
        }
        else{
            Log.i(TG, "No connected bluetooth device found")
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyResponse(message: String){
        if (bleDevice != null){
            psmCharacteristic.value = message.toByteArray()
            gattServer?.notifyCharacteristicChanged(bleDevice, psmCharacteristic,true)
        }
        else{
            Log.i(TG, "No connected bluetooth device found")
        }
    }


    var psmCharacteristic = BluetoothGattCharacteristic(
        myCharacteristicsUUID2,
        BluetoothGattCharacteristic.PROPERTY_INDICATE or BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
    )
    private fun setupGattService(): BluetoothGattService {
        val service = BluetoothGattService(myServiceUUID2, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val messageCharacteristic = BluetoothGattCharacteristic(
            myCharacteristicsUUID1,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(messageCharacteristic)
        service.addCharacteristic(psmCharacteristic)
        return service
    }

    private inner class GattServerCallback : BluetoothGattServerCallback() {
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
                deviceName = device.name
                viewModel?.updateData("",deviceName)
                Log.d(TG, "Server connected to ${device.address}")
                try {
                    bleDevice = device
                }
                catch (e:Error){
                    bleDevice= null
                    Log.e(TG, e.toString())
                }
            } else {
                bleDevice = null
                Log.d(TG, "Server disconnected from ${device.name}")
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

                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,null)
                if(value != null){
                    PacketManager.parse(value, deviceName)

                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (device != null) {
                Log.i(TG, "Read request received from device: ${device.address}, ${device.name}")
            }
            val sendPacket = PacketManager.remotePacket
            if (sendPacket != null) {
                val status = gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, sendPacket.toData())
                if(status == true){
                    PacketManager.remotePacket = null
                }
                Log.i(TG, "Response status to device: $status")
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            Log.d(TG, "Notification sent to ${device?.address}, status: $status")
        }
    }
}
