
package com.example.bleexample.models

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.bleexample.utils.PREFERENCES_NAME
import com.example.bleexample.utils.WEBHOOK_URL_KEY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class DataStoreHandler(private val context:Context){
    companion object{
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)
    }

    val getData: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[WEBHOOK_URL_KEY] ?: ""
        }

    suspend fun saveData(name: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBHOOK_URL_KEY] = name
        }
    }
}