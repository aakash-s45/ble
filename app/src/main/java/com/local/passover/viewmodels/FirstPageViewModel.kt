package com.local.passover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.passover.core.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FirstPageState{
    object Connected: FirstPageState
    object NotConnected: FirstPageState
    object Loading: FirstPageState
    data class Error(val message: String): FirstPageState
}

@HiltViewModel
class FirstPageViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
): ViewModel() {
    val TAG = "FirstPageViewModel"
    val connectionState = connectionRepository.connectionState
    val hasCredentials = connectionRepository.hasCredentials

    fun removeSavedCredentials(){
        viewModelScope.launch {
            connectionRepository.closeConnection()
            connectionRepository.clearCredentials()
        }
    }
}