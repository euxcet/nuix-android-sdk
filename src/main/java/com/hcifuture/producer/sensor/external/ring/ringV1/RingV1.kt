package com.hcifuture.producer.sensor.external.ring.ringV1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.BytesDataCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.data.RingTouchData
import com.hcifuture.producer.sensor.external.MyBleManager
import com.hcifuture.producer.sensor.external.ring.RingSpec

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.common.core.DataByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
class RingV1(
    val context: Context,
    private val deviceName: String,
    private val address: String,
) : NuixSensor(), ConnectionObserver {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var buffer = ByteArray(0)
    private var connection: MyBleManager? = null
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
                val serviceMap = mutableMapOf<UUID, List<UUID>>(
                    RingV1Spec.SPP_SERVICE_UUID to mutableListOf(
                        RingV1Spec.SPP_READ_CHARACTERISTIC_UUID,
                        RingV1Spec.SPP_WRITE_CHARACTERISTIC_UUID
                    ),
                    RingV1Spec.NOTIFY_SERVICE_UUID to mutableListOf(
                        RingV1Spec.NOTIFY_READ_CHARACTERISTIC_UUID,
                        RingV1Spec.NOTIFY_WRITE_CHARACTERISTIC_UUID
                    )
                )
                connection = MyBleManager.connect(context, address, serviceMap) {
                    it.connectionObserver = this@RingV1
                }
                if (!connection!!.isConnected) {
                    status = NuixSensorState.DISCONNECTED
                    return@launch
                }
                status = NuixSensorState.CONNECTED

                val sppJob = scope.launch {
                    connection?.getNotificationFlow(RingV1Spec.SPP_SERVICE_UUID, RingV1Spec.SPP_READ_CHARACTERISTIC_UUID)?.collect {
                        extractImu(it).map { data ->
                            /**
                             * TODO: use timestamps from the sensor?
                             */
                            count += 1
                            _imuFlow.emit(RingImuData(data.first, data.second))
                        }
                    }
                }

                val notifyJob = scope.launch {
                    connection?.getNotificationFlow(RingV1Spec.NOTIFY_SERVICE_UUID, RingV1Spec.NOTIFY_READ_CHARACTERISTIC_UUID)?.collect {
                        handleNotification(it)?.let { data ->
                            _touchFlow.emit(RingTouchData(RingV1Spec.code2TouchEvent(data), System.currentTimeMillis()))
                        }
                    }
                }

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
                writeNotify(
                    Data(DataByteArray.from(36.toByte(), 2, 224.toByte(), 5, 0).value)
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
        connection?.connectionObserver = null
        connection?.disconnect()
        status = NuixSensorState.DISCONNECTED
    }

    private suspend fun writeSpp(data: String) {
        try {
            connection?.writeData(RingV1Spec.SPP_SERVICE_UUID, RingV1Spec.SPP_WRITE_CHARACTERISTIC_UUID, Data.from(data + "\r\n"))
        }
        catch (e: Exception) {
            Log.e("Nuix", "Error ${e.message}")
        }
        delay(200)
    }

    private suspend fun writeNotify(data: Data) {
        try {
            connection?.writeData(RingV1Spec.NOTIFY_SERVICE_UUID, RingV1Spec.NOTIFY_WRITE_CHARACTERISTIC_UUID, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }
        catch (e: Exception) {
            Log.e("Nuix", "Error ${e.message}")
        }
        delay(200)
    }

    private fun handleNotification(data: Data): Int? {
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

    private fun extractImu(data: Data): List<Pair<List<Float>, Long>> {
        buffer += data.value ?: ByteArray(0)
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

    override fun onDeviceConnecting(device: BluetoothDevice) {
        status = NuixSensorState.CONNECTING
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        status = NuixSensorState.CONNECTED
    }

    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        status = NuixSensorState.DISCONNECTED
    }

    override fun onDeviceReady(device: BluetoothDevice) {

    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {

    }

    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        disconnect()
    }
}