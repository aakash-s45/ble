package com.example.bleexample.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BLEConnectionReceiver: BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        Log.d("BLEConnectionReceiver", "onReceive")
        if(p1!=null){
            Log.d("BLEConnectionReceiver", p1.action.toString())

        }
        else{
            Log.d("BLEConnectionReceiver", "Intent is null")
        }
    }

}