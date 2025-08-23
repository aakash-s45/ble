package com.local.passover.clipboard

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.local.passover.classes.PacketManager
import timber.log.Timber

class ClipboardActivity : ComponentActivity() {
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
            ?: "<empty>"

        Timber.tag("ClipboardActivity").d("📑 Clipboard now contains: “$text”")
        PacketManager.sendClipboard(text)
        finish()
    }
}