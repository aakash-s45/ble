package com.local.passover.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

fun isAccessibilityServiceRunning(context: Context, serviceClass: Class<*>): Boolean{
    val componentName = ComponentName(context, serviceClass)
    val enabledServiceSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServiceSetting)

    while (colonSplitter.hasNext()){
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == componentName){
            return true
        }
    }
    return false
}