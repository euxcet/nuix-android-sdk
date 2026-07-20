package com.hcifuture.producer.sensor.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lossless diagnostic record for a 0x3C waveform BLE notification.
 *
 * Record layout (all numeric fields little-endian):
 * uint32 recordLength (bytes after this field), uint8 version,
 * uint64 appSequence, uint64 elapsedRealtimeNanos, uint64 wallClockMillis,
 * uint16 notifyLength, followed by the complete BLE notification.
 */
class RingV2RawNotifyData(
    private val appSequence: Long,
    private val elapsedRealtimeNanos: Long,
    private val wallClockMillis: Long,
    private val notify: ByteArray,
) : BytesData {
    override fun toBytes(): ByteArray {
        val payloadSize = 1 + 8 + 8 + 8 + 2 + notify.size
        return ByteBuffer.allocate(4 + payloadSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payloadSize)
            .put(1)
            .putLong(appSequence)
            .putLong(elapsedRealtimeNanos)
            .putLong(wallClockMillis)
            .putShort(notify.size.toShort())
            .put(notify)
            .array()
    }
}
