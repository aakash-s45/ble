//
//package com.local.passover.classes
//
//import android.content.Context
//import androidx.datastore.core.DataStore
//import androidx.datastore.preferences.core.Preferences
//import androidx.datastore.preferences.core.edit
//import androidx.datastore.preferences.core.stringPreferencesKey
//import androidx.datastore.preferences.preferencesDataStore
//import com.local.passover.utils.PREFERENCES_NAME
//import com.local.passover.utils.allowedNotification
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.flow.map
//import org.json.JSONObject
//
//
//class DataStoreHandler(private val context: Context) {
//    private val urlKey = stringPreferencesKey("url")
//    private val authKey = stringPreferencesKey("auth")
//    private val contactsKey = stringPreferencesKey("contacts")
//    private val packageNameKey = stringPreferencesKey("packageName")
//
//    companion object{
//        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)
//    }
//
//    // Save URL
//    suspend fun saveUrl(url: String) {
//        context.dataStore.edit { preferences ->
//            preferences[urlKey] = url
//        }
//    }
//
//    suspend fun saveAuth(auth: String) {
//        context.dataStore.edit { preferences ->
//            preferences[authKey] = auth
//        }
//    }
//
//    // Get URL
//    val url: Flow<String?> = context.dataStore.data
//        .map { preferences ->
//            preferences[urlKey] ?: ""
//        }
//
//    // Get URL directly as String (for background tasks)
//    suspend fun getUrlDirect(): String {
//        val preferences: Preferences = context.dataStore.data.first()
//        return preferences[urlKey] ?: ""
//    }
//
//    suspend fun getAuthDirect(): String {
//        val preferences: Preferences = context.dataStore.data.first()
//        return preferences[authKey] ?: ""
//    }
//
//    // Save Contacts
//    suspend fun saveContacts(contacts: Map<String, String>) {
//        val contactsJson = JSONObject(contacts).toString()
//        context.dataStore.edit { preferences ->
//            preferences[contactsKey] = contactsJson
//        }
//    }
//
//    // Get Contacts
//    val contacts: Flow<Map<String, String>> = context.dataStore.data
//        .map { preferences ->
//            val contactsJson = preferences[contactsKey] ?: "{}"
//            val jsonObject = JSONObject(contactsJson)
//            val contactsMap = mutableMapOf<String, String>()
//            jsonObject.keys().forEach { key ->
//                contactsMap[key] = jsonObject.getString(key)
//            }
//            contactsMap
//        }
//
//    suspend fun getContactsDirect(): Map<String, String> {
//        val preferences: Preferences = context.dataStore.data.first()
//        val contactsJson = preferences[contactsKey] ?: "{}"
//        val jsonObject = JSONObject(contactsJson)
//        val contactsMap = mutableMapOf<String, String>()
//        jsonObject.keys().forEach { key ->
//            contactsMap[key] = jsonObject.getString(key)
//        }
//        return contactsMap
//    }
//
//    // Clear contacts from DataStore
//    suspend fun clearContacts() {
//        context.dataStore.edit { preferences ->
//            preferences.remove(contactsKey) // Remove the contacts entry
//        }
//    }
//
//    // Get app info (retrieve all apps with their notification statuses)
//    suspend fun getAppInfoMap(): Map<String, List<Boolean>> {
//        val preferences: Preferences = context.dataStore.data.first()
//        allowedNotification
////        if no data present, use allowedNotification as default values
//
//        val appInfoJson = preferences[packageNameKey] ?: "{}"
//        var jsonObject = JSONObject(appInfoJson)
//
//        val appInfoMap = mutableMapOf<String, List<Boolean>>()
//        allowedNotification.forEach { (appName, allowedNotifications) ->
//            appInfoMap[appName] = listOf(allowedNotifications[0], allowedNotifications[1])
//        }
//
//        jsonObject.keys().forEach { key ->
//            val valuesArray = jsonObject.getJSONArray(key)
//            appInfoMap[key] = listOf(valuesArray.getBoolean(0), valuesArray.getBoolean(1))
//        }
//
//        return appInfoMap
//    }
//
//
//
//    // Save app info (package name and list of two boolean values)
//    suspend fun saveAppInfo(appPackageName: String, notificationEnabled: Boolean, mediaNotificationEnabled: Boolean) {
//        val currentAppData = getAppInfoMap().toMutableMap()
//        currentAppData[appPackageName] = listOf(notificationEnabled, mediaNotificationEnabled)
//
//        (currentAppData as Map<*, *>?)?.let {
//            JSONObject(it).toString()
//        }?.let { jsonAppInfo ->
//            context.dataStore.edit { preferences ->
//                preferences[packageNameKey] = jsonAppInfo
//            }
//        }
//    }
//
//    // Clear all app info from DataStore
//    suspend fun clearAppInfo() {
//        context.dataStore.edit { preferences ->
//            preferences.remove(packageNameKey)
//        }
//    }
//}
