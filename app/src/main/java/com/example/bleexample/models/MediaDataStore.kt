package com.example.bleexample.models

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.palette.graphics.Palette
import com.example.bleexample.services.BLEConnectionService
import com.example.bleexample.utils.imageString

object MediaDataStore{
    var defaultArtwork:Bitmap? = null
    val initialMediaState = CurrentMedia("","","",100.0,false,"",0.0,"", null, palette = null, volume = 0.0f)
    var mediaState = initialMediaState
        private set
    private lateinit var application: Application
    private var viewModel: MediaViewModel? = null

    init {
        val imageData = Base64.decode(imageString, Base64.DEFAULT)
        defaultArtwork = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        if(mediaState.artwork == null && defaultArtwork != null){
            mediaState = mediaState.copy(artwork = defaultArtwork)
        }
    }

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
            var _volume = maxOf(0f, mediaState.volume+change)
            _volume = minOf(mediaState.volume+change, 1f)
            mediaState = mediaState.copy(volume = _volume)
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