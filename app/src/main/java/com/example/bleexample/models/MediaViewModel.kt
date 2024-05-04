package com.example.bleexample.models

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


//Message received: M_Meet Me Halfway_The Black Eyed Peas__289.341_156.74562258299144_false_Safari
class MediaViewModel:ViewModel(){
    private val _mediaState = MutableStateFlow(CurrentMedia("","","",100.0,false,"",0.0,"", null))
    private var timerJob:Job? = null

    var mediaState: StateFlow<CurrentMedia> = _mediaState.asStateFlow()

    fun updateData(dataString: String, deviceName: String = ""){
        _mediaState.update {
            setMediaData(dataString, it, deviceName)
        }
        startElapsedTimer()
    }

    fun updateArtwork(artwork: Bitmap?){
        if(artwork == null){
            Log.i("MediaViewModel", "No artwork data")
            return
        }
        Log.i("MediaViewModel", "Updating artwork")
        _mediaState.update {
            updateAlbumArt(artwork, it)
        }
        Log.i("MediaViewModel", _mediaState.toString())
    }

    fun updateElapsedTime(elapsedTime: Double){
        _mediaState.update {
            it.copy(elapsed = elapsedTime*_mediaState.value.duration)
        }
    }

    fun togglePlayPause(){
        _mediaState.update {
            it.copy(playbackRate = !it.playbackRate)
        }
        startElapsedTimer()
    }


    fun startElapsedTimer(){
        if (!mediaState.value.playbackRate) {
            timerJob?.cancel()
        } else {
            timerJob = viewModelScope.launch {
                while (mediaState.value.elapsed < mediaState.value.duration){
                    _mediaState.update {
                        it.copy(elapsed = it.elapsed + 1)
                    }
                    if(_mediaState.value.elapsed >= _mediaState.value.duration){
                        _mediaState.update {
                            it.copy(playbackRate = false)
                        }
                    }
                    delay(1000)
                }
            }
        }
    }
}

data class CurrentMedia(
    val title:String,
    val artist: String,
    val album: String,
    val duration: Double,
    val playbackRate: Boolean,
    val bundle: String,
    val elapsed: Double,
    val deviceName: String,
    val artwork: Bitmap?,
)

fun setMediaData(dataString: String, currentMediaState: CurrentMedia, deviceName: String = ""):CurrentMedia {
    var title: String = ""
    var artist: String = ""
    var album: String = ""
    var duration: Double = 0.0
    var elapsed: Double = 0.0
    var playbackRate: Boolean = false
    var bundle: String = ""
//    var artwork: Bitmap? = null

    val parts = dataString.split("_")
    if(parts.isNotEmpty() && parts[0] != "M"){
        return currentMediaState.copy(deviceName = deviceName)
    }
    if (parts.size == 8) {
        title = parts[1]
        artist = parts[2]
        album = parts[3]
        duration = parts[4].toDoubleOrNull() ?: 0.0
        elapsed = parts[5].toDoubleOrNull() ?: 0.0
        playbackRate = parts[6].toBoolean()
        bundle = parts[7]
    }
    Log.i("MVModel", "Updated media data")
//    TODO: set the value of artwork later
    return currentMediaState.copy(title=title, artist=artist, album=album, duration=duration, playbackRate=playbackRate, bundle=bundle, elapsed=elapsed, deviceName=deviceName)
}

fun updateAlbumArt(artwork: Bitmap, currentMediaState: CurrentMedia):CurrentMedia {
    return currentMediaState.copy(artwork=artwork)
}


class MediaState {
    private var title: String = ""
    private var artist: String = ""
    private var album: String = ""
    private var duration: Double = 0.0
    private var elapsed: Double = 0.0
    private var playbackRate: Boolean = false
    private var bundle: String = ""
    private var artwork: Bitmap? = null


    fun setMediaData(dataString: String) {
        val parts = dataString.split("_")
        if(parts.isNotEmpty() && parts[0] != "M"){
            return
        }
        if (parts.size == 8) {
            this.title = parts[1]
            this.artist = parts[2]
            this.album = parts[3]
            this.duration = parts[4].toDoubleOrNull() ?: 0.0
            this.elapsed = parts[5].toDoubleOrNull() ?: 0.0
            this.playbackRate = parts[6].toBoolean()
            this.bundle = parts[7]
        }
        Log.i("MVModel", "Updated media data")
    }

    fun getMedia(): Map<String, Any> {
        val dataObject = mutableMapOf<String, Any>()
        dataObject["title"] = title
        dataObject["artist"] = artist
        dataObject["album"] = album
        dataObject["duration"] = duration
        dataObject["elapsed"] = elapsed
        dataObject["playbackRate"] = playbackRate
        dataObject["bundle"] = bundle
        return dataObject
    }

}
