package com.local.passover.classes

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.local.passover.Message
import com.local.passover.clipboard.ClipboardHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import javax.inject.Inject


@HiltViewModel
class AppViewModel @Inject constructor(private  val repository: AppRepository, private val clipboardHandler: ClipboardHandler): ViewModel(){
    private var timerJob: Job? = null

    val isNotificationListenerEnabled: LiveData<Boolean> = repository.isNotificationListenerEnabled

    init {

    }
    fun updateClipboardData(data: Message.ClipBoard, deviceName: String?){
        clipboardHandler.addDataToClipboard(data.text, data.origin, deviceName)
    }
}
