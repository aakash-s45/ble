//package com.local.passover.classes
//
//import timber.log.Timber
//import java.io.File
//import java.net.Socket
//
//fun sendFile(ip: String = "192.168.31.180", port: Int, file: File) {
//    Thread {
//        try {
//            val socket = Socket(ip, port)
//            val output = socket.getOutputStream()
//            val input = file.inputStream()
//
//            input.copyTo(output)
//
//            output.flush()
//            output.close()
//            input.close()
//            socket.close()
//            Timber.tag("FileTransfer").d( "File sent successfully")
//        } catch (e: Exception) {
//            Timber.tag("FileTransfer").e(e, "Failed to send file: ${e.message}")
//        }
//    }.start()
//}
