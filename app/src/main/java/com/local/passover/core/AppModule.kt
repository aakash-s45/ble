package com.local.passover.core

import android.content.Context
import android.net.nsd.NsdManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {ctx.preferencesDataStoreFile("passover_prefs")}
        )

    @Provides
    @Singleton
    fun provideNsdManager(@ApplicationContext ctx: Context): NsdManager =
        ctx.getSystemService(Context.NSD_SERVICE) as NsdManager


}