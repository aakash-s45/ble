package com.local.passover.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHandler  @Inject constructor(@ApplicationContext private val context: Context) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastClipData: String? = null
    private var isAddingData = false


    fun checkClipboard() {
        if (isAddingData) return

        val currentClipData = clipboardManager.primaryClip?.getItemAt(0)?.let { item ->
            when {
                item.text != null -> item.text.toString()
                item.uri != null -> getBase64FromUri(item.uri)
                else -> null
            }
        }

        if (currentClipData != null && currentClipData != lastClipData) {
            lastClipData = currentClipData
            gotNewData(currentClipData)
        }
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

    fun addDataToClipboard(data: String, type: String, deviceName:String? = "Remote") {
        Log.i("Clipboard", "Adding clipboard data: $data")
        isAddingData = true

        val clip: ClipData = when (type) {
            "txt" -> ClipData.newPlainText("text", data)
            "img" -> {
                if (isValidBase64Image(data)) {
                    val decodedBytes = Base64.decode(data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    val imageUri = saveImageToMediaStore(context, bitmap)
                    if(imageUri!=null){
                        ClipData.newUri(context.contentResolver, "from $deviceName", imageUri)
                    }
                    else{
                        Log.i("Clipboard", "image uri data null")
                        return
                    }
                } else {
                    Log.e("Clipboard", "Invalid base64 image data")
                    return
                }
            }
            else -> {
                Log.e("Clipboard", "Unsupported type: $type")
                return
            }
        }

        clipboardManager.setPrimaryClip(clip)
        lastClipData = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
        isAddingData = false
    }

    private fun saveImageToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Clipboard_Image_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipboardImages")
        }

        // Insert the image into the MediaStore
        val imageUri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        // Write the bitmap to the MediaStore using the URI
        imageUri?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    // If compression failed, delete the Uri and return null
                    contentResolver.delete(uri, null, null)
                    return null
                }
            }
        }

        return imageUri
    }

    private fun isValidBase64Image(base64Data: String): Boolean {
        return try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            bitmap != null
        } catch (e: IllegalArgumentException) {
            Log.e("Base64Validation", "Invalid base64 string", e)
            false
        } catch (e: Exception) {
            Log.e("Base64Validation", "Error decoding base64 image", e)
            false
        }
    }
    private fun gotNewData(data: String) {
        // Handle the new clipboard data here
        Log.i("Clipboard", "New clipboard data: $data")
    }
}