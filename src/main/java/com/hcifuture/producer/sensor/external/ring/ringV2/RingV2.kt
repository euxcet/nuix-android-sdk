package com.hcifuture.producer.sensor.external.ring.ringV2

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.BytesDataCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.data.RingTouchData
import com.hcifuture.producer.sensor.data.RingTouchEvent
import com.hcifuture.producer.sensor.data.RingV2AudioData
import com.hcifuture.producer.sensor.data.RingV2PPGData
import com.hcifuture.producer.sensor.data.RingV2StatusData
import com.hcifuture.producer.sensor.data.RingV2StatusType
import com.hcifuture.producer.sensor.data.RingV2TouchRawData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import java.util.Arrays
import kotlin.experimental.and

@SuppressLint("MissingPermission")
class RingV2(
    val context: Context,
    private val deviceName: String,
    private val address: String,
) : NuixSensor() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var buffer = ByteArray(0)
    private lateinit var readCharacteristic: ClientBleGattCharacteristic
    private lateinit var writeCharacteristic: ClientBleGattCharacteristic
    private var connection: ClientBleGatt? = null
    private val _imuFlow = MutableSharedFlow<RingImuData>()
    private val _touchEventFlow = MutableSharedFlow<RingTouchData>()
    private val _touchRawFlow = MutableSharedFlow<RingV2TouchRawData>()
    private val _statusFlow = MutableSharedFlow<RingV2StatusData>()
    private val _audioFlow = MutableSharedFlow<RingV2AudioData>()
    private val _ppgFlow = MutableSharedFlow<RingV2PPGData>()
    override val name: String = "RING[${deviceName}|${address}]"
    override val flows = mapOf(
        RingSpec.imuFlowName(this) to _imuFlow.asSharedFlow(),
        RingSpec.touchEventFlowName(this) to _touchEventFlow.asSharedFlow(),
        RingSpec.touchRawFlowName(this) to _touchRawFlow.asSharedFlow(),
        RingSpec.statusFlowName(this) to _statusFlow.asSharedFlow(),
        RingSpec.audioFlowName(this) to _audioFlow.asSharedFlow(),
        RingSpec.ppgFlowName(this) to _ppgFlow.asSharedFlow(),
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow(),
    )
    override val defaultCollectors: Map<String, Collector> = mapOf<String, Collector>(
        RingSpec.imuFlowName(this) to
                BytesDataCollector(listOf(this), listOf(_imuFlow.asSharedFlow()), "ringV2[${address}]IMU.bin"),
        RingSpec.touchEventFlowName(this) to
                BytesDataCollector(listOf(this), listOf(_touchEventFlow.asSharedFlow()), "ringV2[${address}]TouchEvent.bin"),
        RingSpec.ppgFlowName(this) to
                BytesDataCollector(listOf(this), listOf(_ppgFlow.asSharedFlow()), "ringV2[${address}]PPG.bin"),
    )
    private var count = 0
    private lateinit var countJob: Job
    private lateinit var connectJob: Job
    private var readJob: Job? = null
//    private var commandJob: Job? = null
    private val zeroGyro: MutableList<Float> = mutableListOf(0.0f, 0.0f, 0.0f)
    private val lastGyro: MutableList<Float> = mutableListOf(0.0f, 0.0f, 0.0f)

    fun calibrate() {
        zeroGyro[0] = lastGyro[0]
        zeroGyro[1] = lastGyro[1]
        zeroGyro[2] = lastGyro[2]
    }

    override fun connect() {
        if (!connectable()) return
        status = NuixSensorState.CONNECTING
        countJob = scope.launch {
//            while (true) {
//                delay(5000)
//                if (count == 0) {
//                    disconnect()
//                } else {
//                    Log.e("Nuix", "Ring fps: ${count / 5}")
//                    count = 0
//                }
//            }
        }
        connectJob = scope.launch {
            try {
                Log.e("Nuix", "RingV2[${address}] connecting")
                connection = ClientBleGatt.connect(context, address, scope)
                if (!connection!!.isConnected) {
                    status = NuixSensorState.DISCONNECTED
                    return@launch
                }

                connection?.requestMtu(517)

                connection!!.connectionState.onEach {
                    if (it == GattConnectionState.STATE_DISCONNECTED) {
                        disconnect()
                    }
                }.launchIn(scope)

                val service = connection!!.discoverServices().findService(RingV2Spec.SERVICE_UUID)!!
                readCharacteristic = service.findCharacteristic(RingV2Spec.READ_CHARACTERISTIC_UUID)!!
                writeCharacteristic = service.findCharacteristic(RingV2Spec.WRITE_CHARACTERISTIC_UUID)!!
                Log.e("Nuix", "RingV2[${address}] get characteristic")
                readJob = readCharacteristic.getNotifications().onEach {
                    val cmd = it.value[2]
                    val subCmd = it.value[3]
                    when {
                        cmd == 0x11.toByte() && subCmd == 0x0.toByte() -> {
                            // software version
                            _statusFlow.emit(
                                RingV2StatusData(
                                type = RingV2StatusType.SOFTWARE_VERSION,
                                softwareVersion = it.value.slice(4 until it.value.size).toString(),
                            ))
                        }
                        cmd == 0x11.toByte() && subCmd == 0x1.toByte() -> {
                            // hardware version
                            _statusFlow.emit(
                                RingV2StatusData(
                                    type = RingV2StatusType.HARDWARE_VERSION,
                                    softwareVersion = it.value.slice(4 until it.value.size).toString(),
                                ))
                        }
                        cmd == 0x12.toByte() && subCmd == 0x0.toByte() -> {
                            // battery level
                            _statusFlow.emit(
                                RingV2StatusData(
                                    type = RingV2StatusType.BATTERY_LEVEL,
                                    batteryLevel = it.value[4].toInt(),
                                ))
                        }
                        cmd == 0x12.toByte() && subCmd == 0x1.toByte() -> {
                            // battery status
                            _statusFlow.emit(
                                RingV2StatusData(
                                    type = RingV2StatusType.BATTERY_STATUS,
                                    batteryStatus = it.value[4].toInt(),
                                ))
                        }
                        cmd == 0x40.toByte() && subCmd == 0x06.toByte() -> {
                            // imu
                            val data = it.value.slice((4 + it.value.size % 2) until it.value.size)
                                .chunked(2)
                                .map { (l, h) -> (l.toInt().and(0xFF) or h.toInt().shl(8)).toFloat() }
                            var tot = 0
                            for (i in data.indices step 6) {
                                val imu = data.slice(i until i + 6).toMutableList()
                                imu[0] *= 9.8f / 1000.0f
                                imu[1] *= 9.8f / 1000.0f
                                imu[2] *= 9.8f / 1000.0f
                                imu[3] *= 3.14f / 180.0f
                                imu[4] *= 3.14f / 180.0f
                                imu[5] *= 3.14f / 180.0f
                                imu[0] = imu[1].also { imu[1] = imu[0] }
                                imu[1] = imu[2].also { imu[2] = imu[1] }
                                imu[3] = imu[4].also { imu[4] = imu[3] }
                                imu[4] = imu[5].also { imu[5] = imu[4] }
                                imu[0] = -imu[0]
                                imu[2] = -imu[2]
                                imu[3] = -imu[3]
                                imu[5] = -imu[5]
                                tot += 1
                                if (tot == 5) {
                                    imu[5] = lastGyro[2]
                                }
                                lastGyro[0] = imu[3]
                                lastGyro[1] = imu[4]
                                lastGyro[2] = imu[5]
                                imu[3] -= zeroGyro[0]
                                imu[4] -= zeroGyro[1]
                                imu[5] -= zeroGyro[2]
                                count += 1
                                _imuFlow.emit(
                                    RingImuData(
                                        data = imu,
                                        timestamp = 0,
                                    )
                                )
                            }
                        }
                        cmd == 0x61.toByte() && subCmd == 0x00.toByte() -> {
                            // touch events, disabled
                        }
                        cmd == 0x61.toByte() && subCmd == 0x01.toByte() -> {
                            var event: RingTouchEvent? = null
                            if (it.value[7].and(0x01) > 0) {
                                event = RingTouchEvent.TAP
                            } else if (it.value[7].and(0x02) > 0) {
                                event = RingTouchEvent.SWIPE_POSITIVE
                            } else if (it.value[7].and(0x04) > 0) {
                                event = RingTouchEvent.SWIPE_NEGATIVE
                            } else if (it.value[7].and(0x08) > 0) {
                                event = RingTouchEvent.FLICK_POSITIVE
                            } else if (it.value[7].and(0x10) > 0) {
                                event = RingTouchEvent.FLICK_NEGATIVE
                            } else if (it.value[7].and(0x20) > 0) {
                                event = RingTouchEvent.HOLD
                            }
                            if (event != null) {
                                _touchEventFlow.emit(
                                    RingTouchData(
                                        data = event,
                                        timestamp = System.currentTimeMillis(),
                                    )
                                )
                            }
                            // touch raw data
                            _touchRawFlow.emit(
                                RingV2TouchRawData(
                                    data = it.value.slice(5 until it.value.size),
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                        }
                        cmd == 0x61.toByte() && subCmd == 0x02.toByte() -> {
                            val event = if (it.value[4].toInt() == 0) {
                                RingTouchEvent.TAP
                            } else if (it.value[4].toInt() == 1) {
                                RingTouchEvent.HOLD
                            } else if (it.value[4].toInt() == 2) {
                                RingTouchEvent.DOUBLE_TAP
                            } else if (it.value[4].toInt() == 3) {
                                RingTouchEvent.DOWN
                            } else if (it.value[4].toInt() == 4) {
                                RingTouchEvent.UP
                            } else {
                                RingTouchEvent.UNKNOWN
                            }
                            _touchEventFlow.emit(
                                RingTouchData(
                                    data = event,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                        }
                        cmd == 0x71.toByte() && subCmd == 0x00.toByte() -> {
                            // microphone
                            val length = it.value[4].toInt().and(0xFF) or it.value[5].toInt().shl(8)
                            val sequenceId = it.value[6].toInt().and(0xFF) or it.value[7].toInt().and(0xFF).shl(8) or
                                             it.value[8].toInt().and(0xFF).shl(16) or it.value[9].toInt().shl(24)
                            _audioFlow.emit(
                                RingV2AudioData(
                                    length = length,
                                    sequenceId = sequenceId,
                                    data = it.value.slice(10 until it.value.size),
                                )
                            )
                        }
                        cmd == 0x31.toByte() -> {
                            _ppgFlow.emit(
                                RingV2PPGData(
                                    type = subCmd.toInt(),
                                    raw = it.value.slice(4 until it.value.size)
                                )
                            )
                        }
                        cmd == 0x32.toByte() -> {
                            _ppgFlow.emit(
                                RingV2PPGData(
                                    type = subCmd + 4,
                                    raw = it.value.slice(4 until it.value.size)
                                )
                            )
                        }
                    }
                }.launchIn(scope)
                Log.e("Nuix", "RingV2[${address}] send commands")
//                commandJob = scope.launch {
                    write(RingV2Spec.GET_CONTROL)
                    write(RingV2Spec.GET_BATTERY_LEVEL)
                    write(RingV2Spec.GET_HARDWARE_VERSION)
                    write(RingV2Spec.GET_SOFTWARE_VERSION)
                    write(RingV2Spec.OPEN_6AXIS_IMU)
                    status = NuixSensorState.CONNECTED
                    Log.e("Nuix", "RingV2[${address}] connected")
//                }
            }
            catch (e: Exception) {
                Log.e("Nuix", "Error $e")
                disconnect()
                return@launch
            }
        }
        scope.launch {
            delay(4000)
            if (status != NuixSensorState.CONNECTED) {
                Log.e("Nuix", "Error: Timeout")
                disconnect()
            }
        }
    }

    override fun disconnect() {
        if (!disconnectable()) return
        Log.e("Nuix", "Manual disconnect")
        connection?.disconnect()
        readJob?.cancel()
        countJob.cancel()
        connectJob.cancel()
        status = NuixSensorState.DISCONNECTED
    }

    suspend fun write(data: ByteArray) {
        try {
            writeCharacteristic.write(DataByteArray(data), writeType = BleWriteType.NO_RESPONSE)
        }
        catch (e: Exception) {
            Log.e("Nuix", "Error $e")
        }
        delay(50)
    }

    suspend fun openGreenPPG(
        freq: Int = 0, // [0: 25hz, 1: 100hz]
    ) {
        write(RingV2Spec.openGreenPPG(freq))
    }

    suspend fun closeGreenPPG() {
        write(RingV2Spec.CLOSE_GREEN_PPG)
    }

    suspend fun openRedPPG(
        freq: Int = 0, // [0: 25hz, 1: 100hz]
    ) {
        write(RingV2Spec.openRedPPG(freq))
    }

    suspend fun closeRedPPG() {
        write(RingV2Spec.CLOSE_RED_PPG)
    }

    suspend fun openMic() {
        write(RingV2Spec.OPEN_MIC)
    }

    suspend fun closeMic() {
        write(RingV2Spec.CLOSE_MIC)
    }

    suspend fun openIMU() {
        write(RingV2Spec.OPEN_6AXIS_IMU)
    }

    suspend fun closeIMU() {
        write(RingV2Spec.CLOSE_6AXIS_IMU)
    }
}
