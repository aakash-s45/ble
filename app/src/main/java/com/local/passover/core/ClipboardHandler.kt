package com.local.passover.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import android.util.Base64
import com.local.passover.MessageOuterClass
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHandler @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val CHANDLER_TAG = "ClipboardHandler"
    private val remoteClipboardDirName = "remote_clipboard"
    private val latestRemoteImageFileName = "latest_remote_clipboard.png"
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun addDataToClipboard(
        data: String,
        type: MessageOuterClass.ClipboardMessage.ClipboardContentType,
        deviceName: String? = "Remote",
    ) {
        Timber.Forest.tag(CHANDLER_TAG).i("Updating clipboard")
        val clip: ClipData = when (type) {
            MessageOuterClass.ClipboardMessage.ClipboardContentType.TXT ->
                ClipData.newPlainText("text", data)

            MessageOuterClass.ClipboardMessage.ClipboardContentType.IMG -> {
                if (isValidBase64Image(data)) {
                    val decodedBytes = Base64.decode(data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap == null) {
                        Timber.Forest.tag(CHANDLER_TAG).w("Decoded bitmap is null")
                        return
                    }

                    val imageUri = saveImageToCache(bitmap)
                    if (imageUri != null) {
                        ClipData.newUri(context.contentResolver, "from $deviceName", imageUri)
                    } else {
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

    fun updateClipboard(message: MessageOuterClass.ClipboardMessage) {
        addDataToClipboard(message.content, message.type)
    }

    private fun saveImageToCache(bitmap: Bitmap): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, remoteClipboardDirName)
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                Timber.Forest.tag(CHANDLER_TAG).w("Failed to create remote clipboard cache dir")
                return null
            }

            cacheDir.listFiles()
                ?.filter { it.name != latestRemoteImageFileName }
                ?.forEach { oldFile ->
                    if (!oldFile.delete()) {
                        Timber.Forest.tag(CHANDLER_TAG).w("Failed to delete stale cached image: ${oldFile.name}")
                    }
                }

            val imageFile = File(cacheDir, latestRemoteImageFileName)
            FileOutputStream(imageFile, false).use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    imageFile.delete()
                    return null
                }
                outputStream.flush()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile,
            )
        } catch (e: Exception) {
            Timber.Forest.tag(CHANDLER_TAG).e(e, "Failed to save remote clipboard image")
            null
        }
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
