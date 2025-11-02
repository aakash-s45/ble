//package com.local.passover.classes
//
//import android.content.Context
//import android.os.Handler
//import android.os.Looper
//import android.widget.Toast
//import dagger.hilt.android.qualifiers.ApplicationContext
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import javax.inject.Inject
//import javax.inject.Singleton
//
//
//@Singleton
//class AppRepository @Inject constructor(
//    @ApplicationContext private val context: Context
//) {
//
//    // Holds the latest piece of text read from the clipboard.
//    private val _latestClip = MutableStateFlow("")
//    val latestClip = _latestClip.asStateFlow()
//
//    private val _isServiceRunning = MutableStateFlow(false)
//    val isServiceRunning = _isServiceRunning.asStateFlow()
//
//    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//
//    fun updateClip(newClip: String) {
//        _latestClip.value = newClip
//    }
//
//    fun setServiceRunning(isRunning: Boolean) {
//        _isServiceRunning.value = isRunning
//    }
//
//
//
//    private val dataStoreHandler: DataStoreHandler by lazy {
//        DataStoreHandler(context)
//    }
//
//    fun clearRepository() {
//        repositoryScope.cancel()
//    }
//
//    private fun showToast(message: String){
//        Handler(Looper.getMainLooper()).post {
//            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
//        }
//    }
//}
