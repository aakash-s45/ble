package com.example.bleexample.utils

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