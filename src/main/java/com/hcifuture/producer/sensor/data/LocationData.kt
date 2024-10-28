package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

data class LocationData(val type: Int, val longitude: Double, val latitude: Double, val timestamp: Long) : BytesData {

    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(Int.SIZE_BYTES + Double.SIZE_BYTES * 2 + Long.SIZE_BYTES)
        byteBuffer.putInt(type).putDouble(longitude).putDouble(latitude).putLong(timestamp)
        return byteBuffer.array()
    }
}
