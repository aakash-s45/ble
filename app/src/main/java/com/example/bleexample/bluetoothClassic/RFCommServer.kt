package com.example.bleexample.bluetoothClassic

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.example.bleexample.Message
import com.example.bleexample.models.PacketManager
import com.example.bleexample.models.TG
import com.example.bleexample.utils.uuidBTClassic
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.min


const val RFTAG = "RFCommServer"

class RFCommServer (private val application: Application){
    private lateinit var audioManager: AudioManager
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var isListening = true
    private var deviceName:String? = null

    @Volatile private var outputStream:OutputStream? = null

    private var serverSocket:BluetoothServerSocket? = null

    init {
        Log.d(RFTAG, "init RFComm server")
        audioManager = application.getSystemService(AudioManager::class.java)
        bluetoothManager = application.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
    }

    fun stop(){

    }

    @SuppressLint("MissingPermission")
    fun startListening(){
        if(!bluetoothAdapter.isEnabled){
            Log.d(TG,"bluetoothAdapter.isEnabled: false")
            return
        }
        try{
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("cBluetoothServer", uuidBTClassic)
            val connectionListeningThread = Thread{
                while (isListening){
                    var socket: BluetoothSocket? = null
                    try{
                        socket = serverSocket?.accept()
                        socket?.let {
                            Log.d(RFTAG, "Connected to ${it.remoteDevice.name}")
                            deviceName = it.remoteDevice.name
//                            handleConnection(it, deviceName)
                            handleConnection(it, deviceName)
                        }
                    } catch (e: IOException) {
                        Log.e(RFTAG, "Socket's accept() method failed", e)
                    } finally {
                        try {
                            socket?.close()
                        } catch (e: IOException) {
                            Log.e(RFTAG, "Could not close the client socket", e)
                        }
                    }
                }
            }
            connectionListeningThread.start()
        }catch (e:IOException){
            Log.e(RFTAG, "Socket's listen() method failed", e)
        }

    }

    private fun handleConnection(socket: BluetoothSocket, deviceName: String?) {
        val inputStream = socket.inputStream
        outputStream = socket.outputStream

        val buffer = ByteArray(4096) // Buffer for reading, size can be adjusted
        val baos = ByteArrayOutputStream()
        var bytes: Int
        Log.i("BluetoothServer", "handle connection v2")

        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read the size of the incoming message
                val sizeBuffer = ByteArray(4)
                bytes = inputStream.read(sizeBuffer)
                Log.i("BluetoothServer", "Received size indicator: $bytes bytes")

                if (bytes == 4) {
                    val dataSize = ByteBuffer.wrap(sizeBuffer).getInt() // Defaults to big-endian
                    Log.i("BluetoothServer", "Message size: $dataSize bytes")

                    // Reset the ByteArrayOutputStream and totalBytesRead for new message
                    baos.reset()
                    var totalBytesRead = 0

                    // Keep reading from the stream until you have all the data
                    while (totalBytesRead < dataSize) {
                        val bytesToRead = min(buffer.size, dataSize - totalBytesRead)
                        val numBytesRead = inputStream.read(buffer, 0, bytesToRead)

                        if (numBytesRead != -1) {
                            baos.write(buffer, 0, numBytesRead)
                            totalBytesRead += numBytesRead
                            Log.i("BluetoothServer", "Bytes read: $numBytesRead, Total: $totalBytesRead, Data Size: $dataSize")
                        } else {
                            // Handle end of stream before expected data size is reached
                            Log.e("BluetoothServer", "Failed to read complete message from stream")
                            break
                        }
                    }

                    if (totalBytesRead == dataSize) {
                        // Finally, send the data to the handler once we have everything
                        val data: ByteArray = baos.toByteArray()
                        Log.i("BluetoothServer", "Successfully read data of size: ${data.size} bytes")
                        readData(data, data.size, deviceName)
                    } else {
                        Log.e("BluetoothServer", "Mismatch in expected and actual data size. Expected: $dataSize, Received: $totalBytesRead")
                    }
                } else {
                    Log.e("BluetoothServer", "Failed to read message size properly")
                }
            } catch (e: IOException) {
                Log.e("BluetoothServer", "Error occurred when reading or writing", e)
                break
            }
        }
        // Close the socket when done
        try {
            socket.close()
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Could not close the connect socket", e)
        }
    }


    @Synchronized
    private fun writeToOutputStream(data: ByteArray) {
        if (outputStream==null){
            Log.e("BluetoothServer", "Output stream is null")
            return
        }

        try {
            outputStream?.write(data)
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Error occurred when writing", e)
        }
    }

    @Synchronized
    private fun writeToOutputStream_V2(data: ByteArray) {
        if (outputStream==null){
            Log.e("BluetoothServer", "Output stream is null")
            return
        }

        try {
            //Send the size of the data first
            val sizeInfo = ByteBuffer.allocate(4).putInt(data.size).array()
            outputStream?.write(sizeInfo)
            // Now send the data
            val bos = BufferedOutputStream(outputStream, 4096) // you can adjust this value and play around

            bos.write(data)
            bos.flush()
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Error occurred when writing", e)
        }
    }




//    private val bluetoothDiscoverable = (application.applicationContext as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
//        if(result.resultCode == Activity.RESULT_CANCELED){
//            Log.e(RFTAG, "Discoverability request denied\"")
//        }else{
//            Log.d("MainActivity", "Device is now discoverable for ${result.data?.getIntExtra(
//                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0
//            )} seconds")
//        }
//    }

    fun enableDiscoverability(seconds: Int = 120){
        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds)
        }
//        bluetoothDiscoverable.launch(discoverableIntent)
    }

    fun writeData(data:ByteArray){
        writeToOutputStream(data)
    }

    private fun readData(buffer:ByteArray, length:Int,  deviceName: String?){
        val data = Message.BPacket.parseFrom(buffer.sliceArray(0 until length))
        PacketManager.packetDelegator(data,  deviceName)
    }
}