package com.local.passover.classes

import android.util.Log
import java.io.File
import java.net.Socket

fun sendFile(ip: String = "192.168.31.180", port: Int, file: File) {
    Thread {
        try {
            val socket = Socket(ip, port)
            val output = socket.getOutputStream()
            val input = file.inputStream()

            input.copyTo(output)

            output.flush()
            output.close()
            input.close()
            socket.close()
            Log.d("FileTransfer", "File sent successfully")
        } catch (e: Exception) {
            Log.e("FileTransfer", "Failed to send file: ${e.message}")
        }
    }.start()
}
