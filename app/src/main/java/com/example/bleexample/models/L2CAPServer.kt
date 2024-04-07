package com.example.bleexample.models
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException

const val TAG1 = "L2CAP"

object L2CAPServer {
    private var app: Application? = null
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var viewModel: BLEScanViewModel
    private lateinit var serversocket: BluetoothServerSocket



    private var gattServer: BluetoothGattServer? = null
    private var gattServerCallback: BluetoothGattServerCallback? = null
    private var gatt: BluetoothGatt? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null

    private fun getBluetoothAdapter(app:Application): BluetoothAdapter {
        this.app = app
        bluetoothManager = app.getSystemService(BluetoothManager::class.java)
        return bluetoothManager.adapter
    }
    fun startServer(app:Application,viewModel: BLEScanViewModel){
        L2CAPServer.bluetoothAdapter = L2CAPServer.getBluetoothAdapter(app)
        this.viewModel = viewModel
        if(L2CAPServer.bluetoothAdapter.isEnabled){
            Log.i(TAG1,"l2cap Bluetooth is enabled")
            setupL2Cap()
        }
        else{
            Log.e(TAG1,"Bluetooth is not enabled")
        }
    }
    @SuppressLint("MissingPermission")
    fun setupL2Cap(){
        serversocket = bluetoothAdapter.listenUsingL2capChannel()
//        val psm = serversocket.psm
//        Log.i(TAG1, "PSM value $psm")
//        val acceptThread = Thread {
//            while (true) {
//                try {
//                    val socket: BluetoothSocket? = serversocket.accept()
//                    Log.i(TAG1, "l2cap connection accepted")
//                    // Handle the accepted socket, e.g., pass it to another thread for communication
//                } catch (e: IOException) {
//                    Log.e(TAG1, "error occurred while accepting $e")
//                    // Handle socket accept error
//                    break
//                }
//            }
//        }
//        acceptThread.start()
    }
    fun getScannedDevice(device: BluetoothDevice){
        Log.i(TAG, "Scanning device: ${device.address}")
        connectToDevice(device)
    }
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice){
        bluetoothAdapter.listenUsingL2capChannel()
        val psm: Int = 0x7925
        Log.i(TAG, "Random uuid: $psm")
        val bluetoothSocket = device.createL2capChannel(psm)
        bluetoothSocket.connect()
        if (bluetoothSocket.isConnected){
            Log.i(TAG, "Socket connected to device")
            val inputStream = bluetoothSocket.inputStream
            val outputStream = bluetoothSocket.outputStream

            val data = "Hello l2cap message"
            outputStream.write(data.toByteArray())

            val buffer = ByteArray(1024)
            val byteReads = inputStream.read(buffer)

            val reveiveData = buffer.copyOf(byteReads).toString(Charsets.UTF_8)
            Log.i(TAG,"REeived l2cap data: $reveiveData")
        }
        else{
            Log.i(TAG, "Socket not connected to device")

        }
    }





//    private fun startUpatingChar(device:BluetoothDevice){
//        var temp1 = 1
//        var updateTimer: Timer? = null
//        Log.i(TAG, "Start updateing char")
//        if (updateTimer == null) {
//            updateTimer = Timer()
//            updateTimer!!.scheduleAtFixedRate(object : TimerTask() {
//                override fun run() {
//                    temp1+=1
////                    writeCharValue(temp1.toString(), device)
//                }
//            }, 0, 1000) // Update every 1000ms (1 second)
//        }
//    }

}