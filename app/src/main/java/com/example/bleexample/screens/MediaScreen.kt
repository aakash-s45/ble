package com.example.bleexample.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bleexample.R
import com.example.bleexample.models.CurrentMedia
import com.example.bleexample.models.MediaViewModel
import com.example.bleexample.models.NewServer
import com.example.bleexample.models.PacketManager
import com.example.bleexample.models.RC
import com.example.bleexample.services.BLEConnectionService
import com.example.bleexample.utils.enableLocation
import com.example.bleexample.utils.isLocationEnabled
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MediaPage(activity: Activity) {
    val viewModel: MediaViewModel = viewModel()
    val currentMedia by viewModel.mediaState.collectAsState()
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden
    )
    val modalScope = rememberCoroutineScope()
    Log.i("MediaPage", "Updated media page")
    MediaPlayer(currentMedia = currentMedia, bottomSheetState = sheetState)
    MyModalBottomSheet(sheetState = sheetState, activity = activity)

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MediaPlayer(currentMedia: CurrentMedia, bottomSheetState: ModalBottomSheetState){
//    Background
    val viewModel:MediaViewModel = viewModel()
    val scrrenPadding = 25.dp
    Log.i("Playbackrate", currentMedia.toString())
    var isPlaying by remember { mutableStateOf(currentMedia.playbackRate) }


    val defaultBackdropColor =Color(53, 23, 23, 255)
//    var bgColor = currentMedia.palette?.darkVibrantSwatch?.rgb
    var bgColor = currentMedia.palette?.darkMutedSwatch?.rgb
    if (bgColor == null) {
        bgColor = defaultBackdropColor.toArgb()
    }
    val view = LocalView.current
    val colorStops = arrayOf(
        0.4f to Color(bgColor),
        1f to Color(0, 0, 0, 200)
    )
    val deviceName = if(currentMedia.bundle.isNotEmpty()){
        "${currentMedia.deviceName} | ${currentMedia.bundle}"
    }
    else{
        currentMedia.deviceName
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = bgColor
        }
    }
    
    Box (modifier = Modifier
        .fillMaxSize()
        .background(color = Color(bgColor))){
    }
    Box (modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(colorStops = colorStops))){
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(scrrenPadding, 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(90.dp))
        AlbumArt(data = currentMedia.artwork, isPlaying = currentMedia.playbackRate)
        Spacer(modifier = Modifier.height(40.dp))
        MusicTitle(currentMedia.title, currentMedia.artist, bottomSheetState = bottomSheetState)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressBar(viewModel, totalTime = currentMedia.duration, elapsedTime = currentMedia.elapsed, deviceName = deviceName)
        Spacer(modifier = Modifier.height(15.dp))
        PlayerButtons(currentMedia) {
            viewModel.togglePlayPause()
        }
        Spacer(modifier = Modifier.height(18.dp))
        VolumeController((currentMedia.volume*100).toDouble(), color = Color(194, 192, 192, 255), viewModel = viewModel)
    }
}

@Composable
fun AlbumArt(data:Bitmap? = null, isPlaying: Boolean = false){
    Log.i("AlbumArtImage", data.toString())
    ImagePlaceholder(data, isPlaying = isPlaying)
}


@Composable
fun ImagePlaceholder(data: Bitmap? = null, isPlaying: Boolean = false){
    val scale by animateFloatAsState(targetValue = if (isPlaying) 1f else 0.85f, label="album art size")

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ){
        if(data == null){
            Image(painter = painterResource(id = R.drawable.placeholder_albumart), contentDescription = "placeholder album art")
        }
        else{
            Image(bitmap = data.asImageBitmap(), contentDescription = "album art", modifier = Modifier.fillMaxSize())
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MusicTitle(title:String, artist:String, bottomSheetState:ModalBottomSheetState){
    val boxHeight = 80.dp
    val modalScope = rememberCoroutineScope()
    Row (horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier){
        Box(modifier = Modifier
            .weight(0.75f)
            .height(boxHeight), contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.05.sp, overflow = TextOverflow.Ellipsis, maxLines = 1)
                Text(text = artist, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                .clickable {
                    modalScope.launch {
                        bottomSheetState.show()
                    }
                }
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
//            viewModel.updateElapsedTime(newValue)
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
                    PacketManager.sendRemotePacket(RC.PLAY)
                },
            contentAlignment = Alignment.Center
        ){
            Icon(painter = painterResource(id = centerImageId), contentDescription = "play pause", tint = tint,
                modifier = Modifier
                    .size(65.dp)
                    .shadow(elevation = shadowElevation)
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
fun VolumeController(volume:Double, viewModel: MediaViewModel, color:Color = Color.White){
//    var currentVolume by remember {
//        mutableStateOf(volume)
//    }
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
                    viewModel.updateVolume(0.0)
                    PacketManager.sendRemotePacket(RC.VOL_MIN)
                },
            tint = color
        )
        Spacer(modifier = Modifier.width(5.dp))
//        SeekBar(fraction = volume, modifier = Modifier.weight(8f))
        CustomSeekBar(targetValue = 100.0, currentValue = volume, primaryColor = color, modifier = Modifier
            .height(6.dp)
            .weight(8f)
            ,onPositionChange = { newPosition ->
                viewModel.updateVolume(newPosition)
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
                    viewModel.updateVolume(1.00)
                    PacketManager.sendRemotePacket(RC.VOL_PLUS)
                }
            ,
            tint = color
        )

    }
}


@Composable
fun CustomSeekBar(
    targetValue: Double,
    currentValue: Double,
    backgroundColor: Color = Color(230, 230, 230, 96),
    primaryColor:Color = Color.White,
    modifier: Modifier = Modifier,
    onPositionChange: (Double) -> Unit,
    onInteractionEnd: (Double) -> Unit = {}
) {
    val _progress = if (targetValue == 0.0) 0.0 else currentValue / targetValue
    val progress by animateFloatAsState(targetValue = _progress.toFloat(), label="seek bar progress")
    var isDragging by remember { mutableStateOf(false) }
    val barShape = RoundedCornerShape(10.dp)
    val barHeight = 6.dp
//    val backgroundColor = Color(230, 230, 230, 96)
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
                .background(color = primaryColor)
                .fillMaxWidth((progress.toFloat()))
        )
    }
}



@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MyModalBottomSheet(sheetState:ModalBottomSheetState, activity:Activity) {
    val viewModel:MediaViewModel = viewModel()

    ModalBottomSheetLayout(sheetState = sheetState,
        sheetBackgroundColor = Color(30, 31, 34),
        sheetShape = RoundedCornerShape(20.dp),
        sheetContent = {
        val context = LocalContext.current

        Column(modifier = Modifier
            .padding(16.dp)) {
            ModalRow(Icons.Rounded.Refresh, "Refresh"){
                NewServer.notifyWithResponse("REFRESH")
                Toast.makeText(context, "Fetching data", Toast.LENGTH_SHORT).show()
            }
            ModalRow(Icons.Default.Info, "Advertise"){
                if(!isLocationEnabled(context) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
                    Toast.makeText(context, "Location is required for this app to run", Toast.LENGTH_SHORT).show()
                    enableLocation(activity)
                }
                if (isLocationEnabled(context)){
                    NewServer.startAdvertising()
                }
            }
            ModalRow(Icons.Rounded.Close, "Disconnect"){
                val intent = Intent(context, BLEConnectionService::class.java)
                intent.action = BLEConnectionService.ACTIONS.STOP.toString()
                context.stopService(intent)
                Toast.makeText(context, "Device disconnected", Toast.LENGTH_SHORT).show()
            }
        }
    }){
//        MediaPage()
    }


}

@Composable
fun ModalRow(imageVector: ImageVector, title: String,  onClick:()->Unit = {}){
    Row(modifier = Modifier
        .padding(10.dp)
        .clickable {
            onClick()
        }) {
        Icon(imageVector = imageVector, contentDescription = title)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.LightGray)
    }
}