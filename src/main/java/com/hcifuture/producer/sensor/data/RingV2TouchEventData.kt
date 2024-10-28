package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

enum class RingV2TouchEvent{
    TAP,
    SWIPE_POSITIVE,
    SWIPE_NEGATIVE,
    FLICK_POSITIVE,
    FLICK_NEGATIVE,
    HOLD,
}

data class RingV2TouchEventData(
    val data: RingV2TouchEvent,
    val timestamp: Long,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(4 + 8)
        byteBuffer.putInt(data.ordinal)
        byteBuffer.putLong(timestamp)
        return byteBuffer.array()
    }
}
