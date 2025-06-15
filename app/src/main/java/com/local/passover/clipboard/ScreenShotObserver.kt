package com.local.passover.clipboard

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.local.passover.classes.PacketManager
import java.io.InputStream


class ScreenshotObserver(
    private val context: Context,
    handler: Handler,
    private val onScreenshotDetected: (Uri) -> Unit
) : ContentObserver(handler) {

    // A set to keep track of recently processed URIs to prevent duplicates.
    private val processedUris = mutableSetOf<Uri>()
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (uri != null && !processedUris.contains(uri)) {
            // Process the specific URI that changed.
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

                    // Check if it was created in the last 15 seconds.
                    val isRecent = (System.currentTimeMillis() / 1000) - dateAdded < 15

                    if (isScreenshot && isRecent) {
                        Log.d("ScreenshotObserver", "✅ Verified screenshot detected: $displayName")

                        handleNewImage(uri)
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

    fun handleNewImage(uri: Uri) {
        Handler(Looper.getMainLooper()).postDelayed({
            getBase64FromUri(uri)?.let { base64 ->
                PacketManager.sendClipboard(base64, "img")
            }
        }, 5000)
    }

    private fun getBase64FromUri(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.buffered()?.use {
                Base64.encodeToString(it.readBytes(), Base64.DEFAULT)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
