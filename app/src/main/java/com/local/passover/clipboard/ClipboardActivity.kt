package com.local.passover.clipboard

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.local.passover.MessageOuterClass
import com.local.passover.core.ConnectionRepository
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ClipboardActivity : ComponentActivity() {
    @Inject lateinit var connectionRepo: ConnectionRepository

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

        Timber.tag("ClipboardActivity").d("Clipboard text: “$text”")
        lifecycleScope.launch {
            sendClipboardData(text)
        }
        finish()
    }

    private fun sendClipboardData(text: String){
        val data = MessageOuterClass
            .ClipboardMessage
            .newBuilder()
            .setType(MessageOuterClass.ClipboardMessage.ClipboardContentType.TXT)
            .setContent(text)
            .build().toByteArray()

        connectionRepo.send(data)
    }
}