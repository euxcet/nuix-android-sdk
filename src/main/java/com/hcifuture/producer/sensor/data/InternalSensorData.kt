package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

data class InternalSensorData(
    val type: Int,
    val data: List<Float>,
    val timestamp: Long,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(4 + data.size * 4 + 8)
        byteBuffer.putInt(type)
        for (value in data) {
            byteBuffer.putFloat(value)
        }
        byteBuffer.putLong(timestamp)
        return byteBuffer.array()
    }
}
