package com.example.bleexample.models

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.util.Log
import com.example.bleexample.ble.BleServer


const val TG = "NewBLEServer"
object NewServer{
    private var app:Application? = null
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var bleServer: BleServer? = null
    private  var count = 0


    fun start(app:Application){
        bluetoothManager = app.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        if(bluetoothAdapter.isEnabled){
            Log.d(TG,"bluetoothAdapter.isEnabled: true")
            bleServer = BleServer(app, bluetoothManager)
            bleServer?.start()
        }
        else{
            Log.d(TG,"bluetoothAdapter.isEnabled: false")
        }
    }

    fun stop(){
        bleServer?.stop()
    }

    fun startAdvertising(){
        bleServer?.startAdvertising()
    }

    fun notifyWithResponse(message: String){
        val _message = "N$message"
        bleServer?.notifyResponse(_message)
    }

}



