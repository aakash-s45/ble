package com.example.bleexample.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.example.bleexample.Message
import com.example.bleexample.utils.defaultMediaData
import com.example.bleexample.utils.imageString
import com.google.protobuf.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class MediaData(
    val title:String,
    val artist: String,
    val album: String,
    val duration: Double,
    val playbackRate: Boolean,
    val bundle: String,
    val elapsed: Double,
    val deviceName: String,
    val artwork: Bitmap?,
    var volume: Float,
    val palette: Palette?,
    val timestamp: Timestamp?
)

@Singleton
class AppRepository @Inject constructor() {
    private val _mediaData = MutableLiveData<MediaData>()
    val mediaData: LiveData<MediaData> = _mediaData

    fun resetToDefault() {
        val imageData = Base64.decode(imageString, Base64.DEFAULT)
        defaultMediaData = defaultMediaData.copy(artwork = BitmapFactory.decodeByteArray(imageData, 0, imageData.size))
        updateData(defaultMediaData)
        defaultMediaData.artwork?.let { updatePalette(it) }
    }
    fun updateData(newData: MediaData) {
        _mediaData.postValue(newData)
        Log.i("Repository", "updated media data")
    }

    fun setMediaData(data: Message.MediaData, deviceName: String = ""){
        var currentData = _mediaData.value
        if (currentData == null){
            currentData = defaultMediaData
        }
        Log.i("Repository", "Updating media data")
        var _data = currentData.copy(
            title = data.title,
            artist = data.artist,
            album = data.album,
            duration = data.duration,
            elapsed = data.elapsed,
            playbackRate = data.playbackRate,
            bundle = data.bundle,
            volume = data.volume,
            timestamp = data.timestamp,
        )
        if (deviceName.isNotBlank()){
            _data = _data.copy(deviceName=deviceName)
        }

        _mediaData.postValue(_data)

    }

    fun updateVolume(volume: Float? = null, change: Float = 0.0f) {
        _mediaData.value?.let {
            val newVolume = volume ?: (it.volume + change)
            PacketManager.sendRemotePacket(RC.SEEK_VOL, newVolume.toDouble())
            updateData(it.copy(volume = newVolume))
        }
    }

    fun togglePlayPause() {
        _mediaData.value?.let {
            val newPlaybackRate = !it.playbackRate
            updateData(it.copy(playbackRate = newPlaybackRate))
        }
        PacketManager.sendRemotePacket(RC.PLAY)
    }

    fun nextMedia(){
        PacketManager.sendRemotePacket(RC.NEXT)
    }

    fun previousMedia(){
        PacketManager.sendRemotePacket(RC.PREV)
    }

    private fun updatePalette(bitmap: Bitmap){
        Log.i("Repository", "Updating palette")
        Palette.from(bitmap).generate { palette ->
            if (palette != null) {
                updateMediaDataByKey("palette", palette)
            }
        }
    }


    fun updateMediaDataByKey(key: String, value: Any) {
        var newMediaData: MediaData? = mediaData.value
        newMediaData = when (key) {
            "title" -> newMediaData?.copy(title = value as String)
            "artist" -> newMediaData?.copy(artist = value as String)
            "album" -> newMediaData?.copy(album = value as String)
            "duration" -> newMediaData?.copy(duration = value as Double)
            "playbackRate" -> newMediaData?.copy(playbackRate = value as Boolean)
            "bundle" -> newMediaData?.copy(bundle = value as String)
            "elapsed" -> newMediaData?.copy(elapsed = value as Double)
            "deviceName" -> newMediaData?.copy(deviceName = value as String)
            "artwork" -> newMediaData?.copy(artwork = value as Bitmap)
            "volume" -> newMediaData?.copy(volume = value as Float)
            "palette" -> newMediaData?.copy(palette = value as Palette)
            "timestamp" -> newMediaData?.copy(timestamp = value as Timestamp)
            else -> newMediaData // Ignore unknown keys
        }
        if (newMediaData != null) {
            updateData(newMediaData)
        }
    }

    fun incrementElapsed(){
        _mediaData.value?.let {
            if(it.playbackRate){
                val newElapsed = (it.elapsed + 1)
                updateData(it.copy(elapsed = newElapsed))
            }
        }
    }

    fun setElapsed(value: Double){
        _mediaData.value?.let {
            updateData(it.copy(elapsed = value))
        }
    }

    fun setElapsedTimerOnRemote(){
        _mediaData.value?.let {
            PacketManager.sendRemotePacket(RC.SEEK, it.elapsed)
        }
    }
}

@HiltViewModel
class AppViewModel @Inject constructor(private  val repository: AppRepository, private val clipboardHandler: ClipboardHandler): ViewModel(){
    val mediaData: LiveData<MediaData> = repository.mediaData
    private var timerJob: Job? = null

    init {
        if(repository.mediaData.value==null){
            repository.resetToDefault()
        }
        else{
            updateElapsedTimer()
        }
    }

    private fun updatePalette(bitmap: Bitmap){
        Log.i("MediaViewModel", "Updating palette")
        Palette.from(bitmap).generate { palette ->
            if (palette != null) {
                repository.updateMediaDataByKey("palette", palette)
            }
        }
    }

    fun updateClipboardData(data: Message.ClipBoard, deviceName: String?){
        clipboardHandler.addDataToClipboard(data.text, data.origin, deviceName)
    }

    fun checkClipboard(){
        clipboardHandler.checkClipboard()
    }

    fun updateArtwork(artwork: Bitmap?){
        if(artwork == null){
            Log.i("Repository", "No artwork data")
            return
        }
        Log.i("Repository", "Updating artwork")
        updatePalette(artwork)
        repository.updateMediaDataByKey("artwork", artwork)
    }

    fun togglePlayPause(){
        repository.togglePlayPause()
    }

    fun next(){
        repository.nextMedia()
    }

    fun previous(){
        repository.previousMedia()
    }

    fun updateVolume(volume:Double? = null, change: Float = 0.0f){
        if (volume != null) {
            repository.updateVolume(volume.toFloat())
        }
        else{
            repository.updateVolume(change = change)
        }
    }


    fun updateMediaData(data: Message.MediaData, deviceName:String = ""){
        repository.setMediaData(data, deviceName)
        updateElapsedTimer()
    }

    private fun updateElapsedTimer(){
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (mediaData.value!!.elapsed < mediaData.value!!.duration){
                repository.incrementElapsed()
                delay(1001)
            }
        }
    }

    fun setElapsedTimer(value:Double){
        repository.setElapsed(value)
//        updateElapsedTimer()
    }


    fun setElapsedTimerOnRemote(){
        repository.setElapsedTimerOnRemote()
    }

}
