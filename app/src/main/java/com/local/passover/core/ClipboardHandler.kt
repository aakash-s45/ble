package com.local.passover.core

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
import com.local.passover.MessageOuterClass
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHandler  @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val CHANDLER_TAG = "ClipboardHandler"
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun addDataToClipboard(data: String, type: MessageOuterClass.ClipboardMessage.ClipboardContentType, deviceName:String? = "Remote") {
        Timber.Forest.tag(CHANDLER_TAG).i("Updating clipboard")
        val clip: ClipData = when (type) {
            MessageOuterClass.ClipboardMessage.ClipboardContentType.TXT ->
                ClipData.newPlainText("text", data)
            MessageOuterClass.ClipboardMessage.ClipboardContentType.IMG -> {
                if (isValidBase64Image(data)) {
                    val decodedBytes = Base64.decode(data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    val imageUri = saveImageToMediaStore(context, bitmap)
                    if(imageUri!=null){
                        ClipData.newUri(context.contentResolver, "from $deviceName", imageUri)
                    }
                    else{
                        Timber.Forest.tag(CHANDLER_TAG).w("image uri data null")
                        return
                    }
                } else {
                    Timber.Forest.tag(CHANDLER_TAG).w("Invalid base64 image data")
                    return
                }
            }
            else -> {
                Timber.Forest.tag(CHANDLER_TAG).w("Unsupported type: $type")
                return
            }
        }

        clipboardManager.setPrimaryClip(clip)
    }

    fun updateClipboard(message: MessageOuterClass.ClipboardMessage){
        addDataToClipboard(message.content, message.type)
    }

    private fun saveImageToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        // TODO: Save to context.cacheDir or a hidden app-specific directory unless the user explicitly saves it.
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
            Timber.Forest.tag(CHANDLER_TAG).e(e, "Invalid base64 string")
            false
        } catch (e: Exception) {
            Timber.Forest.tag(CHANDLER_TAG).e(e, "Error decoding base64 image")
            false
        }
    }

}