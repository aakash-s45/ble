package com.local.passover.viewmodels

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.passover.utils.FileLoggingTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogViewerViewModel : ViewModel() {

    private val _logContent = MutableStateFlow<List<String>>(emptyList())
    val logContent = _logContent.asStateFlow()

    private val _fontSize = MutableStateFlow(12)
    val fontSize = _fontSize.asStateFlow()

    fun loadLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFile = File(context.filesDir, FileLoggingTree.LOG_FILE_NAME)
                if (logFile.exists()) {
                    val lines = logFile.readLines()
                    _logContent.value = lines
                } else {
                    _logContent.value = listOf("Log file is empty or does not exist.")
                }
            } catch (e: Exception) {
                _logContent.value = listOf("Error reading log file: ${e.message}")
            }
        }
    }

    fun clearLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFile = File(context.filesDir, FileLoggingTree.LOG_FILE_NAME)
                if (logFile.exists()) {
                    logFile.writeText("") // Clear the file content
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                // Reload logs, which will now be empty
                loadLogs(context)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to clear logs", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveLogsToDownloads(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFile = File(context.filesDir, FileLoggingTree.LOG_FILE_NAME)
                if (!logFile.exists() || logFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Log file is empty, nothing to save.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val logText = logFile.readText()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "AppLogs_$timestamp.txt"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream == null) {
                            throw Exception("Failed to get output stream.")
                        }
                        outputStream.write(logText.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Logs saved to Downloads", Toast.LENGTH_LONG).show()
                    }
                    // Clear logs after successful save
                    clearLogs(context)
                } else {
                    throw Exception("Failed to create MediaStore entry.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving logs: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun zoomIn() {
        if (_fontSize.value < 24) {
            _fontSize.update { it + 2 }
        }
    }

    fun zoomOut() {
        if (_fontSize.value > 8) {
            _fontSize.update { it - 2 }
        }
    }
}
