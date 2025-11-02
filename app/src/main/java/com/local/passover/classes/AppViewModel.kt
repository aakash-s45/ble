//package com.local.passover.classes
//
//import androidx.lifecycle.ViewModel
//import com.local.passover.Message
//import com.local.passover.clipboard.ClipboardHandler
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.StateFlow
//import javax.inject.Inject
//
//
//@HiltViewModel
//class AppViewModel @Inject constructor(private  val repository: AppRepository, private val clipboardHandler: ClipboardHandler): ViewModel(){
//    val isServiceRunning: StateFlow<Boolean> = repository.isServiceRunning
//
//    fun updateClipboardData(data: Message.ClipBoard, deviceName: String?){
//        clipboardHandler.addDataToClipboard(data.text, data.origin, deviceName)
//    }
//}
