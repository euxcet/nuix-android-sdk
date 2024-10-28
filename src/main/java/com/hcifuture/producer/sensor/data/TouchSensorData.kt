package com.hcifuture.producer.sensor.data

import android.graphics.Path
import java.nio.ByteBuffer

class TouchSensorData(
    val path: Path,
): BytesData {
    override fun toBytes(): ByteArray {
        val pathData = path.approximate(0.5f)
        val byteBuffer = ByteBuffer.allocate(4 + 4 * pathData.size)
        byteBuffer.putInt(pathData.size)
        for (data in pathData) {
            byteBuffer.putFloat(data)
        }
        return byteBuffer.array()
    }
}