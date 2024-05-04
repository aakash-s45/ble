package com.example.bleexample.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bleexample.R
import com.example.bleexample.models.CurrentMedia
import com.example.bleexample.models.MediaViewModel
import com.example.bleexample.models.PacketManager
import com.example.bleexample.models.RC
import com.example.bleexample.utils.imageString

private val hexArray = "0123456789ABCDEF".toCharArray()
fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    for (j in bytes.indices) {
        val v = bytes[j].toInt() and 0xFF

        hexChars[j * 2] = hexArray[v ushr 4]
        hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

@Preview(showSystemUi = true)
@Composable
fun MediaPage() {
    val viewModel: MediaViewModel = viewModel()
    val currentMedia by viewModel.mediaState.collectAsState()
    Log.i("MediaPage", "Updated media page")
//    val byteArray1 = imageString.substring(0,16)
//    val decodedBytes = Base64.decode(byteArray1, Base64.DEFAULT)
//    val decodedBytes = Base64.decode(byteArray1, Base64.DEFAULT)
//    Log.i("Base64Substring", decodedBytes.contentToString())
//    Log.i("Base64Substring", bytesToHex(decodedBytes))
//    val byteArray = Base64.decode(imageString, Base64.DEFAULT)
//    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
//    Log.i("TempArt", bitmap.height.toString())
//    viewModel.updateArtwork(bitmap)
//    Image(bitmap = bitmap.asImageBitmap(), contentDescription ="temp")
    MediaPlayer(currentMedia = currentMedia, viewModel)

}

@Composable
fun MediaPlayer(currentMedia: CurrentMedia, viewModel: MediaViewModel){
//    Background
    val scrrenPadding = 25.dp
    Log.i("Playbackrate", currentMedia.toString())
    var isPlaying by remember { mutableStateOf(currentMedia.playbackRate) }

    Box (modifier = Modifier
        .fillMaxSize()
        .background(color = Color(55, 69, 94, 255))){
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(scrrenPadding, 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = {
            val decodedBytes = Base64.decode(imageString, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            viewModel.updateArtwork(bitmap)
        }) {
            Text(text = "Update image")
        }
        Spacer(modifier = Modifier.height(90.dp))
        AlbumArt(isPlaying = currentMedia.playbackRate)
        Spacer(modifier = Modifier.height(40.dp))
        MusicTitle(currentMedia.title, currentMedia.artist)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressBar(viewModel, totalTime = currentMedia.duration, elapsedTime = currentMedia.elapsed, deviceName = currentMedia.deviceName)
        Spacer(modifier = Modifier.height(15.dp))
        PlayerButtons(currentMedia) {
            viewModel.togglePlayPause()
        }
//        PlayerButtons(isPlaying = currentMedia.playbackRate) {
//            isPlaying = !isPlaying
//        }
        Spacer(modifier = Modifier.height(18.dp))
        VolumeController(30.0)
    }
}

@Composable
fun AlbumArt(data:Bitmap? = null, isPlaying: Boolean = false){
    val scale by animateFloatAsState(targetValue = if (isPlaying) 1f else 0.85f, label="album art size")
    Log.i("AlbumArtImage", data.toString())
    if (data != null) {
        Image(bitmap = data.asImageBitmap(), contentDescription = "album art")
    }
    else{
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .scale(scale)
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ){
            Image(painter = painterResource(id = R.drawable.placeholder_albumart), contentDescription = "placeholder album art")
        }
    }
}

@Composable
fun MusicTitle(title:String, artist:String){
    val boxHeight = 80.dp
    Row (horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier){
        Box(modifier = Modifier
            .weight(0.75f)
            .height(boxHeight), contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.05.sp)
                Text(text = artist, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Color.LightGray)
            }
        }
        Box (modifier = Modifier
            .weight(0.25f)
//            .background(color = Color.Black)
            .height(boxHeight), contentAlignment = Alignment.CenterEnd
        ){
            Box (modifier = Modifier
                .clip(CircleShape)
                .background(color = Color(255, 255, 255, 60))
            ){
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "menu",
                    tint = Color.White,
                    modifier = Modifier
                        .size(30.dp)
                        .rotate(90f)
                        .padding(5.dp)
                )
            }
        }
    }
}


@Composable
fun ProgressBar(viewModel: MediaViewModel, totalTime: Double, elapsedTime:Double, deviceName:String = ""){
    val isTotalTimeEnabled = true
    val backgroundColor = Color(230, 230, 230, 96)

//    elapsedTime1 = elapsedTime
    Column {
        Log.i("SeekSlider", totalTime.toString())
        Log.i("SeekSlider", elapsedTime.toString())
//        Progress bar
        CustomSeekBar(targetValue = totalTime, currentValue = elapsedTime, modifier = Modifier.height(6.dp), onInteractionEnd = {}, onPositionChange ={newValue->
            viewModel.updateElapsedTime(newValue)
//            PacketManager.sendRemotePacket(RC.SEEK, newValue)
        } )
        Spacer(modifier = Modifier.height(10.dp))
//        progress time
        Row (
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
        ){
            val fontSize = 13.sp
            Text(text = convertSecondsToTime(elapsedTime), color = backgroundColor, fontSize = fontSize)
            CurrentDevice(deviceName, fontSize, backgroundColor)
            if(isTotalTimeEnabled){
                Text(text = convertSecondsToTime(totalTime), color = backgroundColor, fontSize = fontSize)
            }
            else{
                Text(text = convertSecondsToTime(totalTime-elapsedTime), color = backgroundColor, fontSize = fontSize)
            }
        }
    }
}


@Composable
fun CurrentDevice(currentDevice: String, fontSize:TextUnit, color: Color){
    Row (verticalAlignment = Alignment.CenterVertically){
        Icon(
            painterResource(id = R.drawable.bluetooth_outlines),
            contentDescription = "blueooth device",
            modifier = Modifier
                .size(13.dp),
            tint = Color.LightGray
        )
        Text(text = currentDevice, color = color, fontSize = fontSize, letterSpacing = 0.01.sp)
    }
}

fun convertSecondsToTime(seconds: Double): String {
    val hours = (seconds / 3600).toInt()
    val minutes = ((seconds % 3600) / 60).toInt()
    val remainingSeconds = (seconds % 60).toInt()

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format("%02d:%02d", minutes, remainingSeconds)
    }
}

@Composable
fun PlayerButtons(currentMedia: CurrentMedia, toggle: ()->Unit){
    val tint = Color.White
    val buttonSize = 40.dp
    val centerImageId = if( currentMedia.playbackRate) R.drawable.pause else  R.drawable.play
    val shadowElevation = 0.dp
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(35.dp, 0.dp)
    ) {
        Icon(painter = painterResource(id = R.drawable.next), contentDescription = "previous", tint = tint, modifier = Modifier
            .size(buttonSize)
            .shadow(elevation = shadowElevation)
            .rotate(180f)
            .clickable {
                PacketManager.sendRemotePacket(RC.PREV)
            }
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable {
                    toggle()
                },
            contentAlignment = Alignment.Center
        ){
            Icon(painter = painterResource(id = centerImageId), contentDescription = "play pause", tint = tint,
                modifier = Modifier
                    .size(65.dp)
                    .shadow(elevation = shadowElevation)
                    .clickable {
                        PacketManager.sendRemotePacket(RC.PLAY)
                    }
            )
        }
        Icon(painter = painterResource(id = R.drawable.next), contentDescription = "next", tint = tint, modifier = Modifier
            .shadow(elevation = shadowElevation, shape = CircleShape, clip = false)
            .size(buttonSize)
            .clickable { PacketManager.sendRemotePacket(RC.NEXT) }
        )
    }
}


@Composable
fun VolumeController(volume:Double){
    var currentVolume by remember {
        mutableStateOf(50.0)
    }
    Row(modifier = Modifier
        .padding(40.dp,0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            painterResource(id = R.drawable.no_sound),
            contentDescription = "mute",
            modifier = Modifier
                .size(18.dp)
                .weight(1f)
                .clickable {
                    currentVolume = 0.0
                    PacketManager.sendRemotePacket(RC.VOL_MIN)
                },
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(5.dp))
//        SeekBar(fraction = volume, modifier = Modifier.weight(8f))
        CustomSeekBar(targetValue = 100.0, currentValue = currentVolume, modifier = Modifier
            .height(6.dp)
            .weight(8f)
            ,onPositionChange = { newPosition ->
                    currentVolume = newPosition*100.0
//
        }, onInteractionEnd = {
                PacketManager.sendRemotePacket(RC.SEEK_VOL, it*100.0)
            })
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painterResource(id = R.drawable.sound_50),
            contentDescription = "full volume",
            modifier = Modifier
                .size(20.dp)
                .weight(1f)
                .clickable {
                    currentVolume = 100.00
                    PacketManager.sendRemotePacket(RC.VOL_PLUS)
                }
            ,
            tint = Color.White
        )

    }
}


@Composable
fun CustomSeekBar(
    targetValue: Double,
    currentValue: Double,
    modifier: Modifier = Modifier,
    onPositionChange: (Double) -> Unit,
    onInteractionEnd: (Double) -> Unit = {}
) {
    val _progress = if (targetValue == 0.0) 0.0 else currentValue / targetValue
    val progress by animateFloatAsState(targetValue = _progress.toFloat(), label="seek bar progress")
    var isDragging by remember { mutableStateOf(false) }
    val barShape = RoundedCornerShape(10.dp)
    val barHeight = 6.dp
    val backgroundColor = Color(230, 230, 230, 96)
    var newValueHolder = currentValue
    Box(
        modifier = modifier
            .fillMaxWidth(1f)
            .height(barHeight)
            .clip(barShape)
            .background(color = backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!isDragging) {
                            val newValue = (offset.x / size.width) * 1.0
                            onPositionChange(newValue)
                            onInteractionEnd(newValue)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        onInteractionEnd(newValueHolder)
                    }

                ) { change, _ ->
                    val newValue = (change.position.x / size.width) * 1.0
                    onPositionChange(newValue)
                    newValueHolder = newValue
                }
            }
    ) {

        Box(
            modifier = modifier
                .height(barHeight)
                .clip(barShape)
                .background(color = Color.White)
                .fillMaxWidth((progress.toFloat()))
        )
    }
}
