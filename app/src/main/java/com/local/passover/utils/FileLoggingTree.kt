package com.local.passover.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(private val context: Context) : Timber.DebugTree() {

    companion object {
        const val LOG_FILE_NAME = "app_logs.txt"
    }

    @SuppressLint("LogNotTimber")
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)

        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (!logFile.exists()) {
                logFile.createNewFile()
            }

            // Using FileWriter with append=true
            FileWriter(logFile, true).use { writer ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                writer.append("$timestamp ${logPriorityToString(priority)}/$tag: $message\n")
                if (t != null) {
                    writer.append(Log.getStackTraceString(t))
                    writer.append("\n")
                }
            }
        } catch (e: Exception) {
            // Log to Logcat if file writing fails
            Log.e("FileLoggingTree", "Error writing to log file", e)
        }
    }

    private fun logPriorityToString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }
}

