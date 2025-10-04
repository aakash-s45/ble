package com.local.passover.bluetoothClassic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.local.passover.Message
import com.local.passover.classes.NetworkManager
import com.local.passover.classes.PacketManager
import com.local.passover.utils.L2CAP_SERVICE_UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.Volatile
import kotlin.math.min


@SuppressLint("MissingPermission")
object BluetoothL2capManager {

    private const val TAG = "BluetoothL2capManager"
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    @Volatile
    private var clientSocket: BluetoothSocket? = null

//    update the viewmodel
    private val _status = MutableStateFlow("Stopped")
    val status = _status.asStateFlow()

    //    current device
    private val _currentClient = MutableStateFlow<String?>(null)
    val currentClient = _currentClient.asStateFlow()


    @SuppressLint("NewApi")
    fun startServer(context: Context) {
        if (scope.isActive){
            Timber.tag(TAG).w("Server is already running or starting.")
            return
        }

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if(bluetoothAdapter == null){
            _status.value = "Bluetooth is not available"
            Timber.tag(TAG).e("Bluetooth is not available")
            return
        }
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        listenForConnection()
    }

    private fun listenForConnection(){
        scope.launch {
            while (isActive){
                var tempServerSocket: BluetoothServerSocket? = null
                try {
                    tempServerSocket = bluetoothAdapter?.listenUsingInsecureL2capChannel()
                    val psm = tempServerSocket!!.psm
                    _status.value = "Server listening with PSM: $psm"
                    Timber.tag(TAG).d("Server listening with PSM: $psm")

                    startPsmAdvertising(psm)

//                  waits for client to connect
                    val socket = tempServerSocket.accept()
//                  client connected: stop advertising and close the server socket

                    stopPsmAdvertising()
                    tempServerSocket.close()

                    _status.value = "Connected to ${socket.remoteDevice.name}"
                    Timber.tag(TAG).d("Connection accepted from ${socket.remoteDevice.address}")
                    clientSocket = socket

                    handleConnection(socket)
                } catch (e: IOException) {
                    if(isActive){
                        Timber.tag(TAG).e(e, "Connection loop error. Restarting...")
                        _status.value = "Error: ${e.message}. Restarting listener."
                    }
                }finally {
                    stopPsmAdvertising()
                    tempServerSocket?.close()
                    clientSocket?.close()
                    clientSocket = null
                    _currentClient.value = null
                    _status.value = "Disconnected. Waiting for new connection..."
                }
            }
            Timber.tag(TAG).i("Connection listener has been stopped.")
        }
    }

    private suspend  fun handleConnection(socket: BluetoothSocket){
        NetworkManager.findMacServer()
        withContext(Dispatchers.IO){
            try {
                val inputStream = socket.inputStream
                Timber.tag(TAG).i("Started listening for messages.")

                while (isActive){
                    val sizeBuffer = ByteArray(4)
                    val bytesReadForSize = inputStream.read(sizeBuffer, 0, 4)

                    if (bytesReadForSize < 4) {
                        Timber.tag(TAG).w("Stream closed while reading size. Disconnecting.")
                        break
                    }

                    val dataSize = ByteBuffer.wrap(sizeBuffer).getInt()
                    if (dataSize <= 0 || dataSize > 1_000_000) { // 1MB limit
                        Timber.tag(TAG).e("Invalid payload size received: $dataSize. Closing connection.")
                        break
                    }

                    Timber.tag(TAG).i("Expecting message of size: $dataSize bytes.")
                    val payload = readExactly(inputStream, dataSize)

                    if (payload != null){
                        val preview = payload.take(20).joinToString("") { "%02x".format(it) }
                        Timber.tag(TAG).i("Message received with ${payload.size} bytes. Preview: $preview...")
                        readData(payload, payload.size, socket.remoteDevice?.name)
                    } else{
                        Timber.tag(TAG).e("Failed to read full payload. Closing connection.")
                        break
                    }
                }
            }catch (e: IOException){
                Timber.tag(TAG).w(e, "Connection lost.")
            }finally {
                Timber.tag(TAG).w("🛑 Stopped listening for messages. Connection closing.")
            }
        }
    }

    @Throws(IOException::class)
    private fun readExactly(inputStream: InputStream, size: Int): ByteArray? {
        if (size < 0) return null

        val dataOutputStream = ByteArrayOutputStream(size)
        val buffer = ByteArray(4096)
        var totalBytesRead = 0

        while (totalBytesRead < size) {
            val bytesToRead = min(buffer.size, size - totalBytesRead)
            val bytesRead = inputStream.read(buffer, 0, bytesToRead)

            if (bytesRead == -1) {
                // End of stream reached prematurely
                return null
            }

            dataOutputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
        }

        return dataOutputStream.toByteArray()
    }

    private fun readData(buffer:ByteArray, length:Int,  deviceName: String?){
//        todo: are we still using this
        val data = Message.BPacket.parseFrom(buffer.sliceArray(0 until length))
        PacketManager.packetDelegator(data,  deviceName)
    }

    fun send(data: ByteArray) {

        val currentSocket = clientSocket
        if (currentSocket == null || !currentSocket.isConnected){
            Timber.tag(TAG).e("Cannot send data, client socket is null or not connected.")
            return
        }
        scope.launch {
            try {
                val outputStream = currentSocket.outputStream
                val dataSize = data.size
                val sizeBuffer = ByteBuffer.allocate(4).putInt(dataSize).array()

                synchronized(outputStream){
                    outputStream.write(sizeBuffer)
                    outputStream.write(data)
                    outputStream.flush()
                }
                Timber.tag(TAG).i("Successfully sent $dataSize bytes.")

            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Error sending data. Connection may be lost")
            }
        }
    }


    private fun startPsmAdvertising(psm: Int) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        // We need 2 bytes for the PSM (UInt16)
        val psmBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(psm.toShort()).array()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(L2CAP_SERVICE_UUID))
            .addServiceData(ParcelUuid(L2CAP_SERVICE_UUID), psmBytes)
            .setIncludeDeviceName(true)
            .build()

        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.tag(TAG).d("BLE advertising started successfully.")
        }
        override fun onStartFailure(errorCode: Int) {
            Timber.tag(TAG).e("BLE advertising onStartFailure: $errorCode")
            _status.value = "BLE Advertising Failed: $errorCode"
        }
    }

    private fun stopPsmAdvertising() {
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        Timber.tag(TAG).d("BLE advertising stopped.")
    }

    fun closeConnection() {
        if(!scope.isActive)return
        _status.value = "Stopping..."
        Timber.tag(TAG).i("Stopping server and closing all connections")
        scope.cancel()
        stopPsmAdvertising()
        clientSocket?.close()
        clientSocket = null
        _currentClient.value = null
        _status.value = "Stopped"
    }
}