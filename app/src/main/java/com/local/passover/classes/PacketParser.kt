package com.local.passover.classes

import android.app.Application
import android.content.Intent
import com.local.passover.Message
import com.local.passover.bluetoothClassic.BluetoothL2capManager
import com.local.passover.services.BLEConnectionService
import timber.log.Timber
import java.time.Instant


const val PTAG = "PacketManager"



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

        Timber.tag(PTAG).i("Packet type: ${packet.type}")
        when(packet.type){
            Message.MessageType.CLIPBOARD -> handleClipboardData(packet.clipboard, deviceName)
            else -> {
                Timber.tag(PTAG).e("Couldn't process the packet: $packet")
            }
        }
    }



    fun handleClipboardData(data: Message.ClipBoard, deviceName: String? = ""){
        Timber.tag(PTAG).i("received: ${data.toString()}")
        viewModel?.updateClipboardData(data,deviceName )

    }

    fun checkClipboard(){
        Timber.tag(PTAG).i("checking clipboard")
    }


    fun handleRemoteEvents(data: Message.RemoteData){


    }

//    fun sendRemotePacket(control:RC, seekValue:Double? = null){
//        var notification_message:String? = "hello"
//        if (Duration.between(lastNotificationInstant, Instant.now()).toMillis() > rateLimit){
//            NewServer.instruct("CMD","${notification_message}")
//            lastNotificationInstant = Instant.now()
//        }
//    }

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

        BluetoothL2capManager.send(message.toByteArray())
    }
}
