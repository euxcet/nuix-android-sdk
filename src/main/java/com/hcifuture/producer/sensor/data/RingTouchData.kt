package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer

enum class RingTouchEvent {
    UNKNOWN,
    // RingV1
    BOTH_BUTTON_PRESS,
    BOTH_BUTTON_RELEASE,
    BOTTOM_BUTTON_CLICK,
    BOTTOM_BUTTON_DOUBLE_CLICK,
    BOTTOM_BUTTON_LONG_PRESS,
    BOTTOM_BUTTON_RELEASE,
    TOP_BUTTON_CLICK,
    TOP_BUTTON_DOUBLE_CLICK,
    TOP_BUTTON_LONG_PRESS,
    TOP_BUTTON_RELEASE,
    // RingV2
    TAP,
    SWIPE_POSITIVE,
    SWIPE_NEGATIVE,
    FLICK_POSITIVE,
    FLICK_NEGATIVE,
    HOLD,
}

data class RingTouchData(
    val data: RingTouchEvent,
    val timestamp: Long,
): BytesData {
    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(4 + 8)
        byteBuffer.putInt(data.ordinal)
        byteBuffer.putLong(timestamp)
        return byteBuffer.array()
    }
}