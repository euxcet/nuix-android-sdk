package com.hcifuture.producer.sensor.external.ring.ringV2

import com.hcifuture.producer.common.utils.FunctionUtils
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.data.RingV2AudioData
import com.hcifuture.producer.sensor.data.RingV2StatusData
import com.hcifuture.producer.sensor.data.RingV2TouchEventData
import com.hcifuture.producer.sensor.data.RingV2TouchRawData
import java.util.UUID

class RingV2Spec {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("bae80001-4f05-4503-8e65-3af1f7329d1f")
        val READ_CHARACTERISTIC_UUID: UUID = UUID.fromString("BAE80011-4F05-4503-8E65-3AF1F7329D1F")
        val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("BAE80010-4F05-4503-8E65-3AF1F7329D1F")

        val GET_TIME             = byteArrayOf(0x00, 0x00, 0x10, 0x01)
        val GET_SOFTWARE_VERSION = byteArrayOf(0x00, 0x00, 0x11, 0x00)
        val GET_HARDWARE_VERSION = byteArrayOf(0x00, 0x00, 0x11, 0x01)
        val GET_BATTERY_LEVEL    = byteArrayOf(0x00, 0x00, 0x12, 0x00)
        val GET_BATTERY_STATUS   = byteArrayOf(0x00, 0x00, 0x12, 0x01)
        val OPEN_6AXIS_IMU       = byteArrayOf(0x00, 0x00, 0x40, 0x06)
        val CLOSE_6AXIS_IMU      = byteArrayOf(0x00, 0x00, 0x40, 0x00)
        val GET_TOUCH            = byteArrayOf(0x00, 0x00, 0x61, 0x00)
        val OPEN_MIC             = byteArrayOf(0x00, 0x00, 0x71, 0x00, 0x01)
        val CLOSE_MIC            = byteArrayOf(0x00, 0x00, 0x71, 0x00, 0x00)
        val GET_NFC              = byteArrayOf(0x00, 0x00, 0x82.toByte(), 0x00)
        val CLOSE_GREEN_PPG      = byteArrayOf(0x00, 0x00, 0x31, 0x02)
        val CLOSE_RED_PPG        = byteArrayOf(0x00, 0x00, 0x32, 0x02)

        fun openGreenPPG(
            time: Int = 30,
            freq: Int = 2, // [0: 25hz, 1: 50hz, 2: 100hz]
            waveform: Boolean = true,
            progress: Boolean = true,
            rr: Boolean = true
        ) : ByteArray {
            return byteArrayOf(
                0x00, 0x00, 0x31, 0x00,
                time.toByte(),
                freq.toByte(),
                if (waveform) 0x01 else 0x00,
                if (progress) 0x01 else 0x00,
                if (rr) 0x01 else 0x00,
            )
        }

        fun openRedPPG(
            time: Int = 30,
            freq: Int = 2, // [0: 25hz, 1: 50hz, 2: 100hz]
            waveform: Boolean = true,
            progress: Boolean = true,
        ) : ByteArray {
            return byteArrayOf(
                0x00, 0x00, 0x32, 0x00,
                time.toByte(),
                freq.toByte(),
                if (waveform) 0x01 else 0x00,
                if (progress) 0x01 else 0x00,
            )
        }
    }
}
