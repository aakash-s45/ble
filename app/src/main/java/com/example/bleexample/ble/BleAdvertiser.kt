package com.example.bleexample.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import com.example.bleexample.models.TG
import com.example.bleexample.utils.myServiceUUID1

@SuppressLint("MissingPermission")
class BleAdvertiser(bluetoothAdapter: BluetoothAdapter){
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertiseSettings: AdvertiseSettings
    private var advertiseData: AdvertiseData
    init {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
//        bluetoothAdapter.setName("New name")
        Log.d(TG, "startAdvertisement: with advertiser $advertiser")
        advertiseSettings = buildAdvertiseSettings()
        advertiseData = buildAdvertiseData()
    }


    @SuppressLint("MissingPermission")
    fun start(){
        if(advertiseCallback == null){
            advertiseCallback = DeviceAdvertiseCallback()
            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(){
        Log.d(TG, "Stopping Advertising with advertiser $advertiser")
        advertiser?.stopAdvertising(advertiseCallback)
        advertiseCallback = null
    }

    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            .build()
    }

    private fun buildAdvertiseData(): AdvertiseData {
        val serviceData = "Hello, World!".toByteArray()
        val dataBuilder = AdvertiseData.Builder()
//            .addServiceUuid(ParcelUuid(UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB")))
            .addServiceUuid(ParcelUuid(myServiceUUID1))
            .setIncludeDeviceName(true)

        return dataBuilder.build()
    }

    private class DeviceAdvertiseCallback : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            // Send error state to display
            val errorMessage = "Advertise failed with error: $errorCode"
            Log.d(TG, errorMessage)
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TG, "Advertising successfully started")
//            NewServer.instruct("CONNECT")
        }
    }
}