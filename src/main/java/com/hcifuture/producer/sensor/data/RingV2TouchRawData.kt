package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

data class RingV2TouchRawData (
    val data: List<Byte>,
    val timestamp: Long,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(data.size + 8)
        for (value in data) {
            byteBuffer.put(value)
        }
        byteBuffer.putLong(timestamp)
        return byteBuffer.array()
    }
}
