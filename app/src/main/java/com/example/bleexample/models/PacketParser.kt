package com.example.bleexample.models

import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

enum class ConnectionState{
    IDLE,
    RECEIVING
}

enum class RC{
    PLAY,
    SEEK,
    SEEK_VOL,
    VOL_PLUS,
    VOL_INC,
    VOL_MIN,
    VOL_DEC,
    NEXT,
    PREV
}

data class BPacket(val type: Char, val seq: Int, val data: ByteArray)

fun BPacket.toData(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.write(type.code)
    val seqBytes = ByteBuffer.allocate(4).putInt(seq).array()
    byteArrayOutputStream.write(seqBytes)
    byteArrayOutputStream.write(data)
    return byteArrayOutputStream.toByteArray()
}




object PacketManager{
     private val INIT:Char = 'I'
    private val GRAPHICS:Char = 'G'
    private val METADATA:Char = 'M'
    private val REMOTE:Char = 'R'

    private var viewModel:MediaViewModel? = null
    private var buffer =  mutableMapOf<Int, ByteArray>()
    private var totalPackets = 0
    private var connectionState:ConnectionState = ConnectionState.IDLE
    private var nextPakcetSeq = -1
    var remotePacket:BPacket? = null
    private var remotePacketReadCount = 3
    private var method = "reliable"

    fun setViewModel(viewModel: MediaViewModel?){
        this.viewModel = viewModel
    }
     fun parse(byteArray: ByteArray, deviceName: String = ""){
         if (this.viewModel == null){
             Log.i("Delegator", "Viewmodel is null")
             return
         }
        if (byteArray.size < 5) {
            Log.i("Delegator", "Not enough bytes to parse")
            // Not enough bytes to parse
            return
        }

        val type = byteArray[0].toInt().toChar()
        val seqBytes = byteArray.sliceArray(1 until 5)
        val seq = ((seqBytes[0].toInt() and 0xFF) shl 24) or
                ((seqBytes[1].toInt() and 0xFF) shl 16) or
                ((seqBytes[2].toInt() and 0xFF) shl 8) or
                (seqBytes[3].toInt() and 0xFF)
        val data = byteArray.sliceArray(5 until byteArray.size)
        packetDelegator(BPacket(type, seq, data), deviceName )
    }


    fun packetDelegator(packet: BPacket, deviceName: String = ""){
        when(packet.type){
            INIT -> handleInitPacket(packet)
            GRAPHICS -> handleGraphicsData(packet)
            METADATA -> handleMetaData(packet, deviceName)
            REMOTE -> handleRemoteEvents(packet)
        }

    }

    fun handleMetaData(packet: BPacket, deviceName: String = ""){
        val data = packet.data.toString(Charsets.UTF_8)
        Log.d("handleMetaData", data)
        viewModel?.updateData(data, deviceName)
    }

    fun handleRemoteEvents(packet: BPacket){


    }
    fun handleGraphicsData(packet: BPacket){
        if (method == "fast"){
            handleGraphicsDataFast(packet)
            return
        }
//        TODO: add time thing also
        Log.i("handleGraphicsData", packet.seq.toString())
        if(packet.seq == this.nextPakcetSeq){
            buffer[packet.seq] = packet.data
            nextPakcetSeq = packet.seq + 1
        }
        if(nextPakcetSeq == totalPackets){
//            val combinedByteArray = buffer.values.flatMap { it.toList() }.toByteArray()
            val combinedByteArray = ByteArrayOutputStream().apply {
                buffer.values.forEach { write(it) }
            }.toByteArray()
//            val decodedBytes = Base64.decode(byteArray1, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(combinedByteArray, 0, combinedByteArray.size)

            Log.i("bitmap", combinedByteArray.size.toString())
            Log.i("bitmap", combinedByteArray.toString())
            Log.i("bitmap", bitmap.height.toString())
            viewModel?.updateArtwork(bitmap)
        }
        else{
            NewServer.notifyWithResponse("ACK:${nextPakcetSeq}")
        }
    }

    fun handleGraphicsDataFast(packet: BPacket){
//        TODO: add time thing also
        Log.i("handleGraphicsDataFast", packet.seq.toString())
        if(packet.data.isNotEmpty()){
            buffer[packet.seq] = packet.data
        }
        if(packet.seq + 1 == totalPackets && buffer.size == totalPackets){
            val combinedByteArray = ByteArrayOutputStream().apply {
                buffer.values.forEach { write(it) }
            }.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(combinedByteArray, 0, combinedByteArray.size)
            viewModel?.updateArtwork(bitmap)
        }
        else if(packet.seq + 1 == totalPackets && buffer.size != totalPackets){
            Log.e("handleGraphicsDataFast", "Maybe some error has occurred, use reliable method")
            Log.e("handleGraphicsDataFast", "seq: ${packet.seq}, totalPackets:$totalPackets, buffer.size:${buffer.size}")
        }
    }

    fun handleInitPacket(packet: BPacket){
        Log.i("handleInitPacket", packet.seq.toString())
        Log.i("NewInitPacket", packet.data.decodeToString())
        method = packet.data.decodeToString()
        buffer.clear()
        totalPackets = packet.seq
        connectionState = ConnectionState.IDLE
        nextPakcetSeq = 0
        NewServer.notifyWithResponse("ACK:0")
//        setup receiving
    }

    fun sendRemotePacket(control:RC, seekValue:Double? = null){
        var _packet:BPacket? = null
        _packet = when(control){
            RC.PLAY -> {
                viewModel?.updateVolume()
                BPacket('R',1, "PLAY".toByteArray())
            }

            RC.NEXT -> {
                BPacket('R',1, "NEXT".toByteArray())
            }

            RC.PREV -> {
                BPacket('R',1, "PREV".toByteArray())
            }

            RC.VOL_PLUS -> {
                BPacket('R',1, "VFULL".toByteArray())
            }

            RC.VOL_MIN -> {
                BPacket('R',1, "VMUTE".toByteArray())
            }

            RC.VOL_INC -> {
                viewModel?.updateVolume(change = 0.0625f)
                BPacket('R',1, "VINC".toByteArray())
            }

            RC.VOL_DEC -> {
                viewModel?.updateVolume(change = -0.0625f)
                BPacket('R',1, "VDEC".toByteArray())
            }


            RC.SEEK -> {
                BPacket('R',2, "SEEKM:$seekValue".toByteArray())
            }

            RC.SEEK_VOL -> {
                BPacket('R',2, "SEEKV:$seekValue".toByteArray())
            }
        }
        if (remotePacket != null && remotePacketReadCount > 0) {
            remotePacketReadCount-=1
            return
        }
        if (_packet == null){
            Log.i("REMOTEControl", "No packet to send")
            return
        }
        if (remotePacket != null && remotePacketReadCount == 0) {
            remotePacketReadCount = 3
        }

        remotePacket = _packet
        NewServer.notifyWithResponse("READ")

//        set the value in the variable if null (3 times only)
//        notify
//        read request complete, set null
    }



}
