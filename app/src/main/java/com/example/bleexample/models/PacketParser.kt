package com.example.bleexample.models

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import com.example.bleexample.Message
import com.example.bleexample.bluetoothClassic.RFTAG
import com.example.bleexample.services.BLEConnectionService
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


    private var artworkData: ByteArray? = null
    var remotePacket:BPacket? = null
    private var remotePacketReadCount = 3
    private var lastNotificationInstant:Instant? = null
    private var rateLimit = 500L
    private  var viewModel:AppViewModel? = null
    private lateinit var application: Application
    private  var clipboardHandler:ClipboardHandler? = null

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
            Message.MessageType.CLIPBOARD -> handleClipboardData(packet.clipboard, deviceName)
            Message.MessageType.GRAPHICS -> handleArtWork(packet.graphic)
            Message.MessageType.MEDIADATA -> handleMediaData(packet.mediaData,deviceName)
//            Message.MessageType.METADATA -> handleInitPacket(packet.metadata)
            Message.MessageType.REMOTE -> handleRemoteEvents(packet.remoteData)
            else -> {
                Log.e(TG, "Couldn't process the packet: $packet")
            }
        }
    }

    private fun handleArtWork(data: Message.Graphic){
        Log.i(RFTAG, "Received Image data of SIZE: ${data.data.size()}")
        artworkData = data.data.toByteArray()
        if(artworkData!=null){
            val artworkByteArray = ByteArrayOutputStream().apply {
                write(artworkData)
            }.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(artworkByteArray, 0, artworkByteArray.size)
            viewModel?.updateArtwork(bitmap)
            notifyService()
        }
    }

    fun handleClipboardData(data: Message.ClipBoard, deviceName: String? = ""){
        Log.i("Clipboard", "received: ${data.toString()}")
        viewModel?.updateClipboardData(data,deviceName )

    }

    fun checkClipboard(){
        Log.i("Clipboard", "checking clipboard")
        viewModel?.checkClipboard()
    }


    fun handleMediaData(data: Message.MediaData,  deviceName: String? = ""){
        Log.d("handleMetaData", data.toString())
        val _deviceName = deviceName ?: ""
        viewModel?.updateMediaData(data, _deviceName)
        notifyService()
    }


    fun handleRemoteEvents(data: Message.RemoteData){


    }

    fun sendRemotePacket(control:RC, seekValue:Double? = null){
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
