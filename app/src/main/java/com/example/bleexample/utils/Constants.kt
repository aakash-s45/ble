package com.example.bleexample.utils

import android.Manifest
import android.os.Build
import java.util.UUID

val myServiceUUID1: UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb")
val myCharacteristicsUUID1: UUID = UUID.fromString("36d4dc5c-814b-4097-a5a6-b93b39085928")
val myCharacteristicsUUID2: UUID = UUID.fromString("7db3e235-3608-41f3-a03c-955fcbd2ea4b")
val deviceAddress:String  = "7C:24:99:ED:EE:4C"
const val SCAN_PERIOD = 5000

////Since the permissions needed for this app are fixed we define them here
//val requiredPermissionsInitialClient =
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//        arrayOf(
//            Manifest.permission.BLUETOOTH_CONNECT,
//            Manifest.permission.BLUETOOTH_SCAN,
//        )
//    } else {
//        arrayOf(
//            Manifest.permission.BLUETOOTH,
//            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.ACCESS_COARSE_LOCATION
//        )
//    }

//Since the permissions needed for this app are fixed we define them here
val requiredPermissionsInitialClient = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )