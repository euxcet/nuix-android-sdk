package com.hcifuture.producer

import com.hcifuture.producer.sensor.data.RingV2RawNotifyData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RingV2RawNotifyDataTest {
    @Test
    fun encodesVersionOneRecordInLittleEndian() {
        val notify = byteArrayOf(0x00, 0x05, 0x3C, 0x02, 0x07, 0x01)
        val encoded = RingV2RawNotifyData(
            appSequence = 9L,
            elapsedRealtimeNanos = 10L,
            wallClockMillis = 11L,
            notify = notify,
        ).toBytes()
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(1 + 8 + 8 + 8 + 2 + notify.size, buffer.int)
        assertEquals(1, buffer.get().toInt())
        assertEquals(9L, buffer.long)
        assertEquals(10L, buffer.long)
        assertEquals(11L, buffer.long)
        assertEquals(notify.size, buffer.short.toInt())
        val actualNotify = ByteArray(notify.size)
        buffer.get(actualNotify)
        assertArrayEquals(notify, actualNotify)
    }
}
