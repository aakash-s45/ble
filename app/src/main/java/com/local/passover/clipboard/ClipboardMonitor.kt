package com.local.passover.clipboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.local.passover.services.MainService
import timber.log.Timber


@SuppressLint("AccessibilityPolicy")
class ClipboardMonitor : AccessibilityService() {
    private val CMON_TAG = "ClipboardMonitor"
    private var currentFocusedApp: String = ""
    val launchReaderOnLongPress = setOf("com.google.android.apps.authenticator2")

    override fun onServiceConnected() {
        super.onServiceConnected()

        startMainService()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        Timber.tag(CMON_TAG).d( "Service Connected")
    }


    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        var shouldTrigger = false
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                updateCurrentAppName(event)
                if(launchReaderOnLongPress.contains(currentFocusedApp)){
                    Timber.tag(CMON_TAG).d( "Detected long press on configured app")
                    launchClipboardActivity()
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val desc = event.contentDescription?.toString() ?: ""

                //check the event.text list for any “copy” or "cut" labels
                val labelMatches = event.text.any { it.toString().contains("copy", ignoreCase = true) || it.toString().contains("cut", ignoreCase = true) }

                if (desc.contains("copy", true) || labelMatches) {
                    Timber.tag(CMON_TAG).d( "Detected copy/cut tap")
                    launchClipboardActivity()
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Catch toasts like “Link copied to clipboard”
                event.text.forEach { t ->
                    if (t.toString().contains("copied", ignoreCase = true)) {
                        Timber.tag(CMON_TAG).d( "Detected copy related toast")
                        launchClipboardActivity()
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

    private fun startMainService(){
        val intent = Intent(applicationContext, MainService::class.java)
        startForegroundService(intent)
    }


    private fun launchClipboardActivity() {
        val intent = Intent(this, ClipboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        startActivity(intent)
    }

    override fun onInterrupt() { /* nothing */ }
}
