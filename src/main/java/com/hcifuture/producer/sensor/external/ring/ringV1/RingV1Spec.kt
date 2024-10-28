package com.hcifuture.producer.sensor.external.ring.ringV1

import com.hcifuture.producer.common.utils.FunctionUtils.reifiedValue
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.data.RingTouchData
import com.hcifuture.producer.sensor.data.RingTouchEvent
import java.util.UUID

class RingV1Spec {
    companion object {
        val SPP_SERVICE_UUID: UUID = UUID.fromString("a6ed0201-d344-460a-8075-b9e8ec90d71b")
        val SPP_READ_CHARACTERISTIC_UUID: UUID = UUID.fromString("a6ed0202-d344-460a-8075-b9e8ec90d71b")
        val SPP_WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("a6ed0203-d344-460a-8075-b9e8ec90d71b")
        val NOTIFY_SERVICE_UUID: UUID = UUID.fromString("0000FF10-0000-1000-8000-00805F9B34FB")
        val NOTIFY_READ_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805F9B34FB")
        val NOTIFY_WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805F9B34FB")

        fun code2TouchEvent(code: Int): RingTouchEvent {
            return when (code) {
                4 -> RingTouchEvent.BOTH_BUTTON_PRESS
                5 -> RingTouchEvent.BOTH_BUTTON_RELEASE
                6 -> RingTouchEvent.BOTTOM_BUTTON_CLICK
                7 -> RingTouchEvent.BOTTOM_BUTTON_DOUBLE_CLICK
                9 -> RingTouchEvent.BOTTOM_BUTTON_LONG_PRESS
                10 -> RingTouchEvent.BOTTOM_BUTTON_RELEASE
                11 -> RingTouchEvent.TOP_BUTTON_CLICK
                12 -> RingTouchEvent.TOP_BUTTON_DOUBLE_CLICK
                14 -> RingTouchEvent.TOP_BUTTON_LONG_PRESS
                15 -> RingTouchEvent.TOP_BUTTON_RELEASE
                else -> RingTouchEvent.UNKNOWN
            }
        }
    }
}