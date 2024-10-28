package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

data class RingImuData (
    val data: List<Float>,
    val timestamp: Long,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(data.size * 4 + 8)
        for (value in data) {
            byteBuffer.putFloat(value)
        }
        byteBuffer.putLong(timestamp)
        return byteBuffer.array()
    }
}