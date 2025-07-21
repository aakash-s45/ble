package com.local.passover.classes

import android.app.Application
import android.content.Intent
import android.util.Log
import com.local.passover.Message
import com.local.passover.bluetoothClassic.NewServer
import com.local.passover.bluetoothClassic.TG
import com.local.passover.services.BLEConnectionService
import java.time.Duration
import java.time.Instant


enum class RC{
    PLAY,
    SEEK,
    NEXT,
    PREV
}



object PacketManager {
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
            Message.MessageType.CLIPBOARD -> handleClipboardData(packet.clipboard, deviceName)
            else -> {
                Log.e(TG, "Couldn't process the packet: $packet")
            }
        }
    }



    fun handleClipboardData(data: Message.ClipBoard, deviceName: String? = ""){
        Log.i("Clipboard", "received: ${data.toString()}")
        viewModel?.updateClipboardData(data,deviceName )

    }

    fun checkClipboard(){
        Log.i("Clipboard", "checking clipboard")
    }


    fun handleRemoteEvents(data: Message.RemoteData){


    }

    fun sendRemotePacket(control:RC, seekValue:Double? = null){
        var notification_message:String? = "hello"
        if (Duration.between(lastNotificationInstant, Instant.now()).toMillis() > rateLimit){
            NewServer.instruct("CMD","${notification_message}")
            lastNotificationInstant = Instant.now()
        }
    }

    fun sendClipboard(data: String, type:String = "txt"){
        val message = Message.BPacket.newBuilder()
            .setType(Message.MessageType.CLIPBOARD)
            .setClipboard(
                Message.ClipBoard.newBuilder()
                    .setText(data)
                    .setOrigin("txt")
                    .setTimestamp(System.currentTimeMillis().toString())
            )
            .build()
        NewServer.send(message)
    }
}
