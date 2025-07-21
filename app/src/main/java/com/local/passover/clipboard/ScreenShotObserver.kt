package com.local.passover.clipboard

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import com.local.passover.classes.sendFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScreenshotObserver(
    private val context: Context,
    handler: Handler,
    private val onScreenshotDetected: (Uri) -> Unit
) : ContentObserver(handler) {

    private val processedUris = mutableSetOf<Uri>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (uri != null && !processedUris.contains(uri)) {
            handleImageUri(uri)
        }
    }

    private fun handleImageUri(uri: Uri) {
        processedUris.add(uri)
        if (processedUris.size > 20) {
            processedUris.remove(processedUris.first())
        }

        try {
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))

                    val isScreenshot = displayName.contains("screenshot", ignoreCase = true) ||
                            path.contains("screenshot", ignoreCase = true)
                    val isRecent = (System.currentTimeMillis() / 1000) - dateAdded < 15

                    if (isScreenshot && isRecent) {
                        Log.d("ScreenshotObserver", "✅ Verified screenshot detected: $displayName")
                        handleNewImage(uri, displayName)
                        onScreenshotDetected(uri)
                    } else {
                        Log.d("ScreenshotObserver", "ℹ️ Ignored image (not a recent screenshot): $displayName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotObserver", "Error processing screenshot URI: $uri", e)
        }
    }

    // Send the actual image file directly - no Base64 overhead
    fun handleNewImage(uri: Uri, displayName: String) {
        coroutineScope.launch {
            try {
                val imageFile = waitForFileAndCopy(uri, displayName, maxRetries = 15)
                if (imageFile != null && imageFile.exists()) {
                    Log.d("ScreenshotObserver", "File ready for sending: ${imageFile.path}, size: ${imageFile.length()}")

                    withContext(Dispatchers.Main) {
                        sendFile(port = 9999, file = imageFile)
                    }

                    // Clean up temp file after a short delay to ensure sending is complete
                    delay(1000)
                    if (imageFile.exists()) {
                        imageFile.delete()
                    }
                } else {
                    Log.e("ScreenshotObserver", "Failed to copy image file after retries")
                }
            } catch (e: Exception) {
                Log.e("ScreenshotObserver", "Error handling new image", e)
            }
        }
    }

    // Copy the image file to cache directory when it's ready
    private suspend fun waitForFileAndCopy(uri: Uri, displayName: String, maxRetries: Int = 15): File? {
        var retryCount = 0
        var delay = 200L // Start with 200ms for pending files

        while (retryCount < maxRetries) {
            try {
                val tempFile = copyUriToTempFile(uri, displayName)
                if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                    Log.d("ScreenshotObserver", "Successfully copied file on attempt ${retryCount + 1}, size: ${tempFile.length()} bytes")
                    return tempFile
                } else {
                    tempFile?.delete() // Clean up empty file
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.d("ScreenshotObserver", "Attempt ${retryCount + 1} failed: $errorMsg")

                // If it's a "pending item" error, wait longer
                if (errorMsg.contains("pending") || errorMsg.contains("trashed")) {
                    delay = 500L // Wait longer for pending files
                }
            }

            retryCount++
            if (retryCount < maxRetries) {
                delay(delay)
                delay = minOf(delay * 2, 3000L).toLong() // Slower increase, max 3 seconds
            }
        }

        Log.e("ScreenshotObserver", "Failed to copy file after $maxRetries attempts")
        return null
    }

    // Copy the URI content to a temporary file
    private fun copyUriToTempFile(uri: Uri, displayName: String): File? {
        return try {
            // Create temp file with proper extension and unique timestamp
            val extension = getFileExtension(displayName) ?: "jpg"
            val timestamp = System.currentTimeMillis()
            val tempFile = File(context.cacheDir, "screenshot_${timestamp}.${extension}")

            // Ensure cache directory exists
            if (!context.cacheDir.exists()) {
                context.cacheDir.mkdirs()
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush() // Ensure data is written
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                Log.d("ScreenshotObserver", "File copied successfully: ${tempFile.path}")
                tempFile
            } else {
                Log.w("ScreenshotObserver", "File copied but is empty or doesn't exist")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e("ScreenshotObserver", "Failed to copy URI to temp file")
            null
        }
    }

    private fun getFileExtension(fileName: String): String? {
        return fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
    }


    fun cleanup() {
        coroutineScope.cancel()
    }
}