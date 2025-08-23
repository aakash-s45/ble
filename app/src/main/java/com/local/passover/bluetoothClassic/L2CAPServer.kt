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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min


@SuppressLint("MissingPermission")
object BluetoothL2capManager {

    private const val TAG = "BluetoothL2capManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var isListening: Boolean = true

//    update the viewmodel
    private val _status = MutableStateFlow("Disconnected")
    val status = _status.asStateFlow()

    //    current device
    private val _currentClient = MutableStateFlow("")
    val currentClient = _currentClient.asStateFlow()


    @SuppressLint("NewApi")
    fun startServer(context: Context) {
        if (serverSocket != null) {
            _status.value = "Server already running."
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        listenForConnection(bluetoothAdapter)
    }

    private fun listenForConnection(bluetoothAdapter: BluetoothAdapter){
        scope.launch {
            while (isListening && isActive){
                try {
                    serverSocket = bluetoothAdapter.listenUsingInsecureL2capChannel()
                    val psm = serverSocket!!.psm
                    _status.value = "Server listening with PSM: $psm"
                    Timber.tag(TAG).d("Server listening with PSM: $psm")

                    startPsmAdvertising(psm)

                    clientSocket = serverSocket?.accept()
                    _status.value = "Connected to ${clientSocket?.remoteDevice?.name}"
                    Timber.tag(TAG).d("Connection accepted from ${clientSocket?.remoteDevice?.address}")
                    _currentClient.value = clientSocket?.remoteDevice?.name.toString()

                    stopPsmAdvertising()
                    listenForMessages()
                } catch (e: IOException) {
                    Timber.tag(TAG).e(e, "Server connection error")
                    _status.value = "Error: ${e.message}"
                    closeConnection()
                }
            }
        }
    }

    fun listenForMessages() {
        NetworkManager.findMacServer()
        scope.launch {
            clientSocket?.inputStream?.let { inputStream ->
                Timber.tag(TAG).i("👂 Started listening for messages.")

                while (isActive) {
                    try {
                        val sizeBuffer = ByteArray(4)
                        val bytesReadForSize = inputStream.read(sizeBuffer, 0, 4)

                        if (bytesReadForSize != 4) {
                            Timber.tag(TAG).d("Bytes read for size: $bytesReadForSize")
                            break
                        }

                        val dataSize = ByteBuffer.wrap(sizeBuffer).getInt()
                        Timber.tag(TAG).i("Expecting message of size: $dataSize bytes.")

                        val payload = readExactly(inputStream, dataSize)

                        if (payload != null) {
                            Timber.tag(TAG).i("✅ Message received with ${payload.size} bytes.")
                            val message = String(payload, 0, payload.size)
                            Timber.tag(TAG).d("Received: $message")
                            readData(payload, payload.size, clientSocket?.remoteDevice?.name)
                            // TODO: Process the complete `payload` here!
                        } else {
                            Timber.tag(TAG).e("Failed to read full payload.")
                            break
                        }

                    } catch (e: IOException) {
                        Timber.tag(TAG).e(e, "Input stream disconnected.")
                        break
                    }
                }
                Timber.tag(TAG).w("🛑 Stopped listening for messages.")

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

    @Synchronized
    fun send(data: ByteArray) {
        scope.launch {
            val outputStream = clientSocket?.outputStream
            if (outputStream == null) {
                Timber.tag(TAG).e("Cannot send data, output stream is null.")
                return@launch
            }

            try {
                val dataSize = data.size
                val sizeBuffer = ByteBuffer.allocate(4).putInt(dataSize).array()
                outputStream.write(sizeBuffer)
                outputStream.write(data)
                outputStream.flush()

                Timber.tag(TAG).i("✅ Successfully sent $dataSize bytes.")

            } catch (e: IOException) {
                Timber.tag(TAG).e(e, "Error occurred when sending data.")
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
        try {
            clientSocket?.close()
            serverSocket?.close()
            stopPsmAdvertising()
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error closing sockets")
        } finally {
            clientSocket = null
            serverSocket = null
            _status.value = "Disconnected"
            _currentClient.value = ""
            isListening = false
        }
    }
}