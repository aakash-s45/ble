package com.example.bleexample.models

import android.app.Application
import android.util.Log
import com.example.bleexample.Message
import com.example.bleexample.bluetoothClassic.RFCommServer


const val TG = "NewBLEServer"
object NewServer{
    private var rfCommServer: RFCommServer? = null

    fun start(application: Application){
        rfCommServer = RFCommServer(application)
        rfCommServer?.startListening()
    }

    fun stop(){
        rfCommServer?.stop()
    }

    fun startDiscovery(){
        rfCommServer?.enableDiscoverability()
    }

    fun instruct(event:String, extraData:String? = ""){
        Log.i(TG, "event in instruct: ${event}, ${extraData}")
        val message = Message.BPacket.newBuilder()
            .setType(Message.MessageType.REMOTE)
            .setRemoteData(
                Message.RemoteData.newBuilder()
                .setEvent(event)
                .setExtraData(extraData)
            )
            .build()
        Log.i(TG, "Sending remote message: $message")
        rfCommServer?.writeData(message.toByteArray())
    }
}



