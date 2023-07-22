package com.example.bleexample.services

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.getSystemService

class BLEConnectionService:Service() {
    private lateinit var bluetoothManager:BluetoothManager
    private lateinit var bluetoothAdapter:BluetoothAdapter
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        return super.onStartCommand(intent, flags, startId)
    }

}