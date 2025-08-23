package com.local.passover.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

const val RECEIVER_TAG = "BLEConnectionReceiver"

class BLEConnectionReceiver: BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        if(p1!=null){
            Timber.tag(RECEIVER_TAG).d(p1.action.toString())
//            when(p1.action.toString()){
//                "NEXT" -> {PacketManager.sendRemotePacket(RC.NEXT)}
//                "PREVIOUS" -> {PacketManager.sendRemotePacket(RC.PREV)}
//                "PAUSE" -> {PacketManager.sendRemotePacket(RC.PLAY)}
//                "PLAY" -> {PacketManager.sendRemotePacket(RC.PLAY)}
//                "READ_CLIPBOARD" -> {PacketManager.checkClipboard()}
//                "SEEK" -> {
//                    val pos = p1.getLongExtra("pos", 0)
//                    PacketManager.sendRemotePacket(RC.SEEK, pos.toDouble())
//                }
//            }

        }
        else{
            Timber.tag(RECEIVER_TAG).e("Intent is null")
        }
    }

}