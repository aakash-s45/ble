package com.local.passover.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.local.passover.classes.PacketManager
import com.local.passover.classes.RC

class BLEConnectionReceiver: BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        Log.d("BLEConnectionReceiver", "onReceive")
        if(p1!=null){
            Log.d("BLEConnectionReceiver", p1.action.toString())
            when(p1.action.toString()){
                "NEXT" -> {PacketManager.sendRemotePacket(RC.NEXT)}
                "PREVIOUS" -> {PacketManager.sendRemotePacket(RC.PREV)}
                "PAUSE" -> {PacketManager.sendRemotePacket(RC.PLAY)}
                "PLAY" -> {PacketManager.sendRemotePacket(RC.PLAY)}
                "READ_CLIPBOARD" -> {PacketManager.checkClipboard()}
                "SEEK" -> {
                    val pos = p1.getLongExtra("pos", 0)
                    PacketManager.sendRemotePacket(RC.SEEK, pos.toDouble())
                }
            }

        }
        else{
            Log.d("BLEConnectionReceiver", "Intent is null")
        }
    }

}