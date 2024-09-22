package com.example.bleexample.utils

import android.content.Context
import android.provider.ContactsContract
import com.example.bleexample.models.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//fun getCurrentTimestamp(): Timestamp {
//    val currentTime = Instant.now()
//    return Timestamp.newBuilder()
//        .setSeconds(currentTime.epochSecond)
//        .setNanos(currentTime.nano)
//        .build()
//}
//
//fun getTimestampDifference(timestamp1: Timestamp, timestamp2: Timestamp): Duration {
//    // Convert Timestamp to Instant
//    val instant1 = Instant.ofEpochSecond(timestamp1.seconds, timestamp1.nanos.toLong())
//    val instant2 = Instant.ofEpochSecond(timestamp2.seconds, timestamp2.nanos.toLong())
//
//    // Calculate the difference
//    return Duration.between(instant1, instant2)
//}

suspend fun importContacts(context: Context, sharedPreferencesHelper: SharedPreferencesHelper) {
    withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val contactsMap = mutableMapOf<String, String>()

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                contactsMap[name] = number
            }
        }

        sharedPreferencesHelper.saveContacts(contactsMap)
    }
}
