package com.example.bleexample.models

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import com.example.bleexample.Message
import com.example.bleexample.bluetoothClassic.RFTAG
import com.example.bleexample.services.BLEConnectionService
import com.google.protobuf.ByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

enum class ConnectionState{
    IDLE,
    RECEIVING
}

enum class RC{
    PLAY,
    SEEK,
    SEEK_VOL,
    VOL_PLUS,
    VOL_INC,
    VOL_MIN,
    VOL_DEC,
    NEXT,
    PREV
}

data class BPacket(val type: Char, val seq: Int, val data: ByteArray)

fun BPacket.toData(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.write(type.code)
    val seqBytes = ByteBuffer.allocate(4).putInt(seq).array()
    byteArrayOutputStream.write(seqBytes)
    byteArrayOutputStream.write(data)
    return byteArrayOutputStream.toByteArray()
}


object PacketManager {

    private const val INIT:Char = 'I'
    private const val GRAPHICS:Char = 'G'
    private const val METADATA:Char = 'M'
    private const val REMOTE:Char = 'R'
    private const val ACCESS:Char = 'A'


    private var buffer =  mutableMapOf<Int, ByteString>()
    private var totalPackets = 0
    private var connectionState:ConnectionState = ConnectionState.IDLE
    private var nextPakcetSeq = -1
    var remotePacket:BPacket? = null
    private var remotePacketReadCount = 3
    private var method = "reliable"
    private var accessKey:String? = null
    private var lastNotificationInstant:Instant? = null
    private var rateLimit = 500L
    private  var viewModel:AppViewModel? = null
    private lateinit var application: Application

    init {
        lastNotificationInstant = Instant.now()
    }

    fun setViewModel(viewModel: AppViewModel){
        this.viewModel = viewModel
    }

    fun setAppContext(app: Application){
        this.application = app
    }

    fun notifyService(){
        val intent = Intent(application, BLEConnectionService::class.java)
        intent.action = BLEConnectionService.ACTIONS.UPDATE.toString()
        application.startService(intent)
    }


    fun packetDelegator(packet: Message.BPacket, deviceName: String? = ""){

        Log.i(TG, "Packet type: ${packet.type}")
        when(packet.type){
            Message.MessageType.CLIPBOARD -> handleClipboardData(packet.clipboard)
            Message.MessageType.GRAPHICS -> handleGraphicsData(packet.graphic)
            Message.MessageType.MEDIADATA -> handleMediaData(packet.mediaData,deviceName)
            Message.MessageType.METADATA -> handleInitPacket(packet.metadata)
            Message.MessageType.REMOTE -> handleRemoteEvents(packet.remoteData)
            else -> {
                Log.e(TG, "Couldn't process the packet: $packet")
            }
        }
    }

    fun handleClipboardData(data: Message.ClipBoard, deviceName: String = ""){

    }


    fun handleMediaData(data: Message.MediaData,  deviceName: String? = ""){
        Log.d("handleMetaData", data.toString())
        val _deviceName = deviceName ?: ""
        viewModel?.updateMediaData(data, _deviceName)
        notifyService()
    }


    fun handleRemoteEvents(data: Message.RemoteData){


    }
    fun handleGraphicsData(data: Message.Graphic){
        if (method == "fast"){
            handleGraphicsDataFast(data)
            return
        }
//        TODO: add time thing also
        Log.i("handleGraphicsData", data.seq.toString())
        if(data.seq == this.nextPakcetSeq){
            buffer[data.seq] = data.data
            nextPakcetSeq = data.seq + 1
        }
        if(nextPakcetSeq == totalPackets){
            val combinedByteArray = ByteArrayOutputStream().apply {
                buffer.values.forEach {
                    write(it.toByteArray())
                }
            }.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(combinedByteArray, 0, combinedByteArray.size)
            viewModel?.updateArtwork(bitmap)
        }
        else{
            NewServer.instruct("TASK","ACK:${nextPakcetSeq}")
        }
    }

    fun handleGraphicsDataFast(data: Message.Graphic){
        Log.i(RFTAG, "PACKET ${data.seq} SIZE: ${data.data.size()}")
//        TODO: add time thing also
        Log.i("handleGraphicsDataFast", data.seq.toString())
        if(!data.data.isEmpty){
            buffer[data.seq] = data.data
        }
        if(data.seq + 1 == totalPackets && buffer.size == totalPackets){
            val combinedByteArray = ByteArrayOutputStream().apply {
                buffer.values.forEach { write(it.toByteArray()) }
            }.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(combinedByteArray, 0, combinedByteArray.size)
            viewModel?.updateArtwork(bitmap)
        }
        else if(data.seq + 1 == totalPackets && buffer.size != totalPackets){
            Log.e("handleGraphicsDataFast", "Maybe some error has occurred, use reliable method")
            Log.e("handleGraphicsDataFast", "seq: ${data.seq}, totalPackets:$totalPackets, buffer.size:${buffer.size}")
        }
    }

    private fun handleInitPacket(data: Message.MetaData){
        Log.d("handleInitPacket", data.size.toString())
        Log.d("NewInitPacket", data.type)
        method = data.type
        buffer.clear()
        totalPackets = data.size
        connectionState = ConnectionState.IDLE
        nextPakcetSeq = 0
        NewServer.instruct("TASK","ACK:0")
    }

    fun sendRemotePacket(control:RC, seekValue:Double? = null, insecure:Boolean = true){
        if(insecure){
            sendRemotePacketInsecure(control, seekValue)
            return
        }

        var command:String? = null
        command = when(control){
            RC.PLAY -> {
//                MediaDataStore.updateVolume()
//                NewServer.instruct("PLAY")
                "PLAY"
            }

            RC.NEXT -> {
                "NEXT"
            }

            RC.PREV -> {
                "PREV"
            }

            RC.VOL_PLUS -> {
                "VFULL"
            }

            RC.VOL_MIN -> {
                "VMUTE"
            }

            RC.VOL_INC -> {
//                TODO: update this
//                MediaDataStore.updateVolume(change = 0.0625f)
                "VINC"
            }

            RC.VOL_DEC -> {
//                MediaDataStore.updateVolume(change = -0.0625f)
                "VDEC"
            }


            RC.SEEK -> {
                "SEEKM:$seekValue"
            }

            RC.SEEK_VOL -> {
                "SEEKV:$seekValue"
            }
        }
        if (remotePacket != null && remotePacketReadCount > 0) {
            remotePacketReadCount-=1
            return
        }
        if (remotePacket != null && remotePacketReadCount == 0) {
            remotePacketReadCount = 3
        }

        NewServer.instruct("CMD", extraData = command)
    }

    private fun sendRemotePacketInsecure(control:RC, seekValue:Double? = null){
        var notification_message:String? = null
        notification_message = when(control){
            RC.PLAY -> {
                "PLAY"
            }

            RC.NEXT -> {
                "NEXT"
            }

            RC.PREV -> {
                "PREV"
            }

            RC.VOL_PLUS -> {
                "VFULL"
            }

            RC.VOL_MIN -> {
                "VMUTE"
            }

            RC.VOL_INC -> {
//                MediaDataStore.updateVolume(change = 0.0625f)
                "VINC"
            }

            RC.VOL_DEC -> {
//                MediaDataStore.updateVolume(change = -0.0625f)
                "VDEC"
            }


            RC.SEEK -> {
                "SEEKM:$seekValue"
            }

            RC.SEEK_VOL -> {
                "SEEKV:$seekValue"
            }
        }
        if (Duration.between(lastNotificationInstant, Instant.now()).toMillis() > rateLimit){
            NewServer.instruct("CMD","${notification_message}")
            lastNotificationInstant = Instant.now()
        }
    }
}
