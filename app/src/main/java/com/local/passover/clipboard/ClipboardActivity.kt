package com.local.passover.clipboard

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.local.passover.MessageOuterClass
import com.local.passover.core.SyncOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

// Transparent activity to send clipboard data to the server
// TODO: instead of tranparent, add an option to add a floating action button
@AndroidEntryPoint
class ClipboardActivity : ComponentActivity() {
    @Inject lateinit var syncOrchestrator: SyncOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = clip
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?: ""

        Log.d("ClipboardActivity", "Clipboard text: “$text”")
        lifecycleScope.launch {
            sendClipboardData(text)
        }
        finish()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    private fun sendClipboardData(text: String) {
        try {
            val clipboardMessage = MessageOuterClass.ClipboardMessage.newBuilder()
                .setType(MessageOuterClass.ClipboardMessage.ClipboardContentType.TXT)
                .setContent(text).build()
            val wrapperMessage = MessageOuterClass.Message.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setClipboard(clipboardMessage)
                .build()
            syncOrchestrator.sendMessage(wrapperMessage)
        } catch (e: Exception) {
            Log.e("ClipboardActivity", "Failed to send clipboard data", e)
        }
    }
}
