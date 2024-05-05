package com.example.bleexample.models

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.palette.graphics.Palette
import com.example.bleexample.services.BLEConnectionService

object MediaDataStore{
    var mediaState = CurrentMedia("","","",100.0,false,"",0.0,"", null, palette = null, volume = 0.0f)
        private set
    private lateinit var application: Application
    private var viewModel: MediaViewModel? = null

    fun setAppContext(app:Application){
        this.application = app
    }

    fun setViewModel(viewModel: MediaViewModel){
        Log.i("MediaDataStore", "adding viewmodel here")
        this.viewModel = viewModel
    }

    private val notificationManager:NotificationManager? = null
    private fun updatePalette(bitmap: Bitmap){
        Log.i("MediaDataStore", "Updating palette")
        Palette.from(bitmap).generate { palette ->
            mediaState = mediaState.copy(palette = palette)
        }
        viewModel?.updatePalette(bitmap)
    }

    fun updateData(dataString: String, deviceName: String = ""){
        mediaState = setMediaData(dataString, mediaState, deviceName)
        Log.i("MediaDataStore", mediaState.toString())
        viewModel?.updateData(dataString, deviceName)
        notifyService()
    }

    fun updateArtwork(artwork: Bitmap?){
        if(artwork == null){
            Log.i("MediaDataStore", "No artwork data")
            return
        }
        Log.i("MediaDataStore", "Updating artwork")
        updatePalette(artwork)
        mediaState = updateAlbumArt(artwork, mediaState)
        viewModel?.updateArtwork(artwork)
        notifyService()
    }

    fun updateVolume(volume:Double? = null, change: Float = 0.0f){
        if (volume != null) {
            mediaState = mediaState.copy(volume = volume.toFloat())
        }
        else{
            mediaState = mediaState.copy(volume = mediaState.volume+change)
        }
        viewModel?.updateVolume(volume, change)
        notifyService()
    }

    fun updateElapsedTime(elapsedTime: Double){
        mediaState = mediaState.copy(elapsed = elapsedTime*mediaState.duration)
        viewModel?.updateElapsedTime(elapsedTime)
        notifyService()
    }

    fun togglePlayPause(){
        mediaState = mediaState.copy(playbackRate = !mediaState.playbackRate)
        viewModel?.togglePlayPause()
        notifyService()
    }

    fun notifyService(){
        val intent = Intent(application, BLEConnectionService::class.java)
        intent.action = BLEConnectionService.ACTIONS.UPDATE.toString()
        application.startService(intent)
    }

}