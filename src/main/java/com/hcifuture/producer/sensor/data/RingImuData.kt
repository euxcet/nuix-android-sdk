package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

data class RingImuData (
    val data: List<Float>,
    val timestamp: Long,
    val ringTicks: Long = 0,  // ring 16384 Hz raw ticks，0 表示未获取
): BytesData {
    override fun toBytes(): ByteArray {
        // ringTicks 仅通过 Kotlin 属性供 JSONL 消费者读取，不写入 binary 格式（BytesDataCollector 的 .bin 文件）
        val byteBuffer = ByteBuffer.allocate(data.size * 4 + 8)
        for (value in data) {
            byteBuffer.putFloat(value)
        }
        byteBuffer.putLong(timestamp)
        return byteBuffer.array()
    }
}