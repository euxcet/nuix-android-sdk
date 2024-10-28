package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

/*
type:
    0 ppg green result
    1 ppg green waveform
    2 ppg green rr
    3 ppg green progress
    4 ppg red result
    5 ppg red waveform
    6 ppg red progress
 */
class RingV2PPGData(
    val type: Int,
    val raw: List<Byte>,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(4 + raw.size)
        byteBuffer.put(type.toByte())
        byteBuffer.put(raw.toByteArray())
        return byteBuffer.array()
    }
}
