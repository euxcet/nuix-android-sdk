package com.hcifuture.producer.sensor.external

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.ktx.suspend
import java.util.UUID

class MyBleManager(
    context: Context,
    private val address: String,
    private val serviceUUIDMap: Map<UUID, List<UUID>>,
    private val onInitialize: ((MyBleManager) -> Unit)?
): BleManager(context) {
    companion object {
        private const val TAG = "MyBleManager"

        suspend fun connect(context: Context, address: String, serviceUUIDMap: Map<UUID, List<UUID>>, onInitialize: ((MyBleManager) -> Unit)?): MyBleManager {
            return MyBleManager(context, address, serviceUUIDMap, onInitialize).apply {
                connect()
            }
        }
    }

    override fun log(priority: Int, message: String) {
        Log.e(TAG, message)
    }

    private val characteristicMap: MutableMap<UUID, MutableMap<UUID, BluetoothGattCharacteristic>> = mutableMapOf()

    override fun initialize() {
        this.onInitialize?.invoke(this)
    }

    fun setMtu(mtu: Int) {
        requestMtu(mtu).enqueue()
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        serviceUUIDMap.keys.forEach { serviceUUID ->
            val service = gatt.getService(serviceUUID) ?: return false
            characteristicMap[serviceUUID] = mutableMapOf()
            val characteristics = serviceUUIDMap[serviceUUID]
            characteristics?.forEach {
                val characteristic = service.getCharacteristic(it) ?: return false
                characteristicMap[serviceUUID]?.set(it, characteristic)
            }
        }
        return true
    }

    override fun onServicesInvalidated() {

    }

    private fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return characteristicMap[serviceUUID]?.get(characteristicUUID)
    }

    suspend fun readData(serviceUUID: UUID, characteristicUUID: UUID): Data? {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        characteristic?.let {
            return readCharacteristic(it).suspend()
        }
        return null
    }

    suspend fun writeData(serviceUUID: UUID, characteristicUUID: UUID, data: Data, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT): Data? {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        characteristic?.let {
            return writeCharacteristic(it, data, writeType).suspend()
        }
        return null
    }

    fun getNotificationFlow(serviceUUID: UUID, characteristicUUID: UUID): Flow<Data>? {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        val sharedFlow = MutableSharedFlow<Data>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        characteristic?.let {
            setNotificationCallback(it).with { _, data ->
                sharedFlow.tryEmit(data)
            }
            enableNotifications(it).enqueue()
            return sharedFlow.asSharedFlow()
        }
        return null
    }

    suspend fun connect() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val device = bluetoothAdapter.getRemoteDevice(address)

        connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .suspend()
    }
}