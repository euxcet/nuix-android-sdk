package com.hcifuture.producer.sensor.external.ring.ringV1

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
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattService
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

@SuppressLint("MissingPermission")
class RingV1(
    val context: Context,
    val deviceName: String,
    val address: String,
) : NuixSensor() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var buffer = ByteArray(0)
    private lateinit var sppReadCharacteristic: ClientBleGattCharacteristic
    private lateinit var sppWriteCharacteristic: ClientBleGattCharacteristic
    private lateinit var notifyReadCharacteristic: ClientBleGattCharacteristic
    private lateinit var notifyWriteCharacteristic: ClientBleGattCharacteristic
    private var connection: ClientBleGatt? = null
    private var count = 0
    private val _imuFlow = MutableSharedFlow<RingImuData>()
    private val _touchFlow = MutableSharedFlow<RingTouchData>()
    override val name: String = "RING[${deviceName}|${address}]"
    override val flows = mapOf(
        RingSpec.imuFlowName(this) to _imuFlow.asSharedFlow(),
        RingSpec.touchEventFlowName(this) to _touchFlow.asSharedFlow(),
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow(),
    )
    override val defaultCollectors: Map<String, Collector> = mapOf<String, Collector>(
        RingSpec.imuFlowName(this) to
                BytesDataCollector(listOf(this), listOf(_imuFlow.asSharedFlow()), "ring[${address}]Imu.bin"),
        RingSpec.touchEventFlowName(this) to
                BytesDataCollector(listOf(this), listOf(_touchFlow.asSharedFlow()), "ring[${address}]Touch.bin"),
    )

    private lateinit var countJob: Job
    private lateinit var connectJob: Job

    override fun connect() {
        if (!connectable()) return
        status = NuixSensorState.CONNECTING

        countJob = scope.launch {
            while (true) {
                delay(4000)
                if (count == 0) {
                    disconnect()
                } else {
                    Log.e("Nuix", "Ring fps: ${count / 4}")
                    count = 0
                }
            }
        }

        connectJob = scope.launch {
            try {
                connection = ClientBleGatt.connect(context, address, scope)
                if (!connection!!.isConnected) {
                    status = NuixSensorState.DISCONNECTED
                    return@launch
                }
                status = NuixSensorState.CONNECTED
                val services = connection!!.discoverServices()
                var sppService: ClientBleGattService = services.findService(RingV1Spec.SPP_SERVICE_UUID)!!
                val notifyService = services.findService(RingV1Spec.NOTIFY_SERVICE_UUID)!!
                sppReadCharacteristic = sppService.findCharacteristic(RingV1Spec.SPP_READ_CHARACTERISTIC_UUID)!!
                sppWriteCharacteristic = sppService.findCharacteristic(RingV1Spec.SPP_WRITE_CHARACTERISTIC_UUID)!!
                notifyReadCharacteristic = notifyService.findCharacteristic(RingV1Spec.NOTIFY_READ_CHARACTERISTIC_UUID)!!
                notifyWriteCharacteristic = notifyService.findCharacteristic(RingV1Spec.NOTIFY_WRITE_CHARACTERISTIC_UUID)!!

                val sppJob = sppReadCharacteristic.getNotifications().onEach {
                    extractImu(it).map { data ->
                        /**
                         * TODO: use timestamps from the sensor?
                         */
                        count += 1
                        _imuFlow.emit(RingImuData(data.first, data.second))
                    }
                }.launchIn(scope)

                val notifyJob = notifyReadCharacteristic.getNotifications().onEach {
                    handleNotification(it)?.let { data ->
                        _touchFlow.emit(RingTouchData(RingV1Spec.code2TouchEvent(data), System.currentTimeMillis()))
                    }
                }.launchIn(scope)

                scope.launch {
                    while (true) {
                        if (!connection!!.isConnected) {
                            sppJob.cancel()
                            notifyJob.cancel()
                            disconnect()
                            break
                        }
                        delay(2000)
                    }
                }

                // Enable touch, TODO: refactor
                notifyReadCharacteristic.write(
                    DataByteArray.from(36.toByte(), 2, 224.toByte(), 5, 0),
                    writeType = BleWriteType.NO_RESPONSE
                )

                val commandList = arrayOf("ENSPP", "ENFAST", "TPOPS=1,1,1",
                    "IMUARG=0,0,0,200", "ENDB6AX")
                for (command in commandList) {
                    Log.e("Nuix", "Write spp: $command")
                    writeSpp(command)
                }
            } catch (e: Exception) {
                Log.e("Nuix", e.message ?: "")
                disconnect()
                return@launch
            }
        }
    }

    override fun disconnect() {
        if (!disconnectable()) return
        countJob.cancel()
        connectJob.cancel()
        connection?.disconnect()
        status = NuixSensorState.DISCONNECTED
    }

    private suspend fun writeSpp(data: String) {
        try {
            sppWriteCharacteristic.write(DataByteArray.from(data + "\r\n"))
        }
        catch (e: Exception) {
            Log.e("Nuix", "Error ${e.message}")
        }
        delay(200)
    }

    private fun handleNotification(data: DataByteArray): Int? {
        // TODO: check crc
        data.getByte(0).let { type ->
            when (type) {
                0x24.toByte() -> {
                    return data.getByte(4)!!.toInt()
                }
                else -> {}
            }

        }
        return null
    }

    private fun extractImu(data: DataByteArray): List<Pair<List<Float>, Long>> {
        buffer += data.value
        val imuList = mutableListOf<Pair<List<Float>, Long>>()
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == 0xAA.toByte() && buffer[i + 1] == 0x55.toByte()) {
                buffer = buffer.copyOfRange(i, buffer.size)
                break
            }
        }
        while (buffer.size > 36 && buffer[0] == 0xAA.toByte() && buffer[1] == 0x55.toByte()) {
            // TODO: check crc
            val byteBuffer = ByteBuffer.wrap(buffer, 4, 32).order(ByteOrder.LITTLE_ENDIAN)
            imuList.add(Pair(listOf(
                byteBuffer.getFloat(), byteBuffer.getFloat(),
                byteBuffer.getFloat(), byteBuffer.getFloat(),
                byteBuffer.getFloat(), byteBuffer.getFloat(),
            ), byteBuffer.getLong()))
            buffer = buffer.copyOfRange(36, buffer.size)
        }
        return imuList
    }
}