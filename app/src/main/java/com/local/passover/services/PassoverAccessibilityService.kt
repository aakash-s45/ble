package com.local.passover.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.local.passover.clipboard.ClipboardActivity
import timber.log.Timber

@SuppressLint("AccessibilityPolicy")
class PassoverAccessibilityService : AccessibilityService() {
    private val TAG = "PassoverAccessibilityService"
    private var currentFocusedApp: String = ""
    val launchReaderOnLongPress = setOf("com.google.android.apps.authenticator2")

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Timber.tag(TAG).d("Screen OFF — pausing sync")
                    sendMainServiceAction(MainService.ACTION_PAUSE)
                }
                Intent.ACTION_SCREEN_ON -> {
                    Timber.tag(TAG).d("Screen ON — resuming sync")
                    sendMainServiceAction(MainService.ACTION_RESUME)
                }
            }
        }
    }

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

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        Timber.Forest.tag(TAG).d("Service Connected")
    }


    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
//        TODO: Update currentFocusedApp on TYPE_WINDOW_STATE_CHANGED events as well
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                updateCurrentAppName(event)
                if(launchReaderOnLongPress.contains(currentFocusedApp)){
                    Timber.Forest.tag(TAG).d( "Detected long press on configured app")
                    launchClipboardActivity()
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val desc = event.contentDescription?.toString() ?: ""

                //check the event.text list for any "copy" or "cut" labels
                val labelMatches = event.text.any { it.toString().contains("copy", ignoreCase = true) || it.toString().contains("cut", ignoreCase = true) }

                if (desc.contains("copy", true) || labelMatches) {
                    Timber.Forest.tag(TAG).d( "Detected copy/cut tap")
                    launchClipboardActivity()
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Catch toasts like "Link copied to clipboard"
                event.text.forEach { t ->
                    if (t.toString().contains("copied", ignoreCase = true)) {
                        Timber.Forest.tag(TAG).d( "Detected copy related toast")
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

    private fun sendMainServiceAction(action: String) {
        val intent = Intent(applicationContext, MainService::class.java).apply {
            this.action = action
        }
        startForegroundService(intent)
    }

    private fun launchClipboardActivity() {
        val intent = Intent(this, ClipboardActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
    }

    override fun onInterrupt() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
    }
}
