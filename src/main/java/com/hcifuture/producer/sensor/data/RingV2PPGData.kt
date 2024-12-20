package com.hcifuture.producer.sensor.data

import android.util.Log
import java.nio.ByteBuffer

/*
type:
    0 ppg green hr
    1 ppg green waveform
    2 ppg green EMPTY
    3 ppg green EMPTY
    4 ppg red spo2
    5 ppg red waveform
    6 ppg red EMPTY
 */
class RingV2PPGData(
    val type: Int,
    val raw: List<Byte>,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(2 + 1 + raw.size)
        byteBuffer.putShort((1 + raw.size).toShort())
        byteBuffer.put(type.toByte())
        byteBuffer.put(raw.toByteArray())
        return byteBuffer.array()
    }
}
