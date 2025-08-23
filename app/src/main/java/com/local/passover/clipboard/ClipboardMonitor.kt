package com.local.passover.clipboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.local.passover.services.BLEConnectionService
import timber.log.Timber

const val CMON_TAG = "ClipboardMonitor"

class ClipboardMonitor : AccessibilityService() {
    private var currentFocusedApp: String = ""

    val launchReaderOnLongPress = setOf<String>("com.google.android.apps.authenticator2")

    override fun onServiceConnected() {
        super.onServiceConnected()
//        TODO: start bluetooth service here

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val intent = Intent(applicationContext, BLEConnectionService::class.java)
        intent.action = BLEConnectionService.ACTIONS.START.toString()
//        startService(intent)
        startForegroundService(intent)
        Timber.tag(CMON_TAG).d( "✅ Service connected")
    }


    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                updateCurrentAppName(event)
                if(launchReaderOnLongPress.contains(currentFocusedApp)){
                    launchReader()
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val desc = event.contentDescription?.toString() ?: ""

                //check the event.text list for any “copy” or "cut" labels
                val labelMatches = event.text.any { it.toString().contains("copy", ignoreCase = true) || it.toString().contains("cut", ignoreCase = true) }

                if (desc.contains("copy", true) || labelMatches) {
                    Timber.tag(CMON_TAG).d( "↪️ Detected Copy-click; desc=“$desc”, textList=${event.text}")
                    launchReader()
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Catch toasts like “Link copied to clipboard”
                event.text.forEach { t ->
                    if (t.toString().contains("copied", ignoreCase = true)) {
                        Timber.tag(CMON_TAG).d( "🔔 Detected notification “$t”, launching reader…")
                        launchReader()
                        return
                    }
                }
            }
        }
    }


    private fun updateCurrentAppName(event: AccessibilityEvent) {
        event.packageName.let {
            currentFocusedApp = it.toString()
        }
    }


    private fun launchReader() {
        val intent = Intent(this, ClipboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        startActivity(intent)
    }

    override fun onInterrupt() { /* nothing */ }
}
