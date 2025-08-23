package com.local.passover.utils

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Log.DEBUG
import android.util.Log.ERROR
import android.util.Log.INFO
import android.util.Log.WARN
import android.widget.Toast
import com.local.passover.utils.FileLoggingTree.Companion.LOG_DIRECTORY
import com.local.passover.utils.FileLoggingTree.Companion.LOG_FILE_NAME
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(private val context: Context) : Timber.Tree() {
    companion object {
        const val LOG_DIRECTORY = "PassoverLogs"
        const val LOG_FILE_NAME = "app_logs.txt"
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < DEBUG) {
            return
        }

        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIRECTORY)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, LOG_FILE_NAME)

            val priorityChar = when (priority) {
                DEBUG -> "D"
                INFO -> "I"
                WARN -> "W"
                ERROR -> "E"
                else -> "?"
            }
            val formattedMessage = "${getCurrentTimestamp()} $priorityChar $tag: $message\n"

            val writer = FileWriter(logFile, true)
            writer.append(formattedMessage)
            writer.flush()
            writer.close()

        } catch (e: Exception) {
            Log.d("FileLoggingTree", "Error writing to log file", e)
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }
}




object LogExporter {
    fun export(context: Context) {
        try {
            val sourceLogFile = getSourceLogFile(context)
            if (sourceLogFile == null || !sourceLogFile.exists()) {
                Toast.makeText(context, "Log file not found!", Toast.LENGTH_SHORT).show()
                return
            }

            // Create a unique name for the exported file using a timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destinationFileName = "passover_logs_$timestamp.txt"

            // Use MediaStore to save the file to the public "Downloads" folder
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, destinationFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                // Copy the log file's content to the new location
                resolver.openOutputStream(uri).use { outputStream ->
                    requireNotNull(outputStream) { "OutputStream cannot be null" }
                    FileInputStream(sourceLogFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "Logs exported to Downloads folder", Toast.LENGTH_LONG).show()
            } else {
                throw Exception("MediaStore URI was null")
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error exporting logs: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun getSourceLogFile(context: Context): File? {
        val logDir = File(context.getExternalFilesDir(null), LOG_DIRECTORY)
        if (!logDir.exists()) {
            return null
        }
        return File(logDir, LOG_FILE_NAME)
    }
}