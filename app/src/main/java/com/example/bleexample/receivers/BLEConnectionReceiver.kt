package com.example.bleexample.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.bleexample.models.PacketManager
import com.example.bleexample.models.RC

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
            }

        }
        else{
            Log.d("BLEConnectionReceiver", "Intent is null")
        }
    }

}