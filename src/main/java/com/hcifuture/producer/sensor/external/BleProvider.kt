package com.hcifuture.producer.sensor.external

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hcifuture.producer.BuildConfig
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorProvider
import com.hcifuture.producer.sensor.external.ring.ringV1.RingV1
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleProvider @Inject constructor(
    @ApplicationContext val context: Context
) : NuixSensorProvider {

    override val requireScan: Boolean = true

    private val _scanResults = MutableStateFlow<List<NuixSensor>>(emptyList())
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val discoveredDevices = mutableMapOf<String, NuixSensor>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("Nuix", "BLE scan failed with error code: $errorCode")
            stopScan()
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val deviceName = device.name ?: ""
        val deviceAddress = device.address

        if (deviceName.startsWith("BCL603")) {
            Log.e("Nuix", "Ring found: $deviceName, address: $deviceAddress")

            val sensor = RingV2(context, deviceName.ifEmpty { "RingV2 Unnamed" }, deviceAddress)

            if (!discoveredDevices.containsKey(deviceAddress)) {
                discoveredDevices[deviceAddress] = sensor
                updateScanResults()
            }
        }
    }

    private fun updateScanResults() {
        _scanResults.value = discoveredDevices.values.toList()
    }

    init {
        initializeBluetooth()
    }

    private fun initializeBluetooth() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    override fun get(): List<NuixSensor> {
        return _scanResults.value
    }

    @SuppressLint("MissingPermission")
    override fun scan(timeout: Long): Flow<List<NuixSensor>> {
        if (!hasBluetoothPermissions()) {
            Log.e("Nuix", "No permission for BLE scan")
            return flow { emit(emptyList()) }
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e("Nuix", "Bluetooth not available or disabled")
            return flow { emit(emptyList()) }
        }

        discoveredDevices.clear()
        _scanResults.value = emptyList()

        startScan()

        CoroutineScope(Dispatchers.Default).launch {
            delay(timeout)
            stopScan()
        }

        return flow {
            emit(emptyList())
            _scanResults.collect { results ->
                emit(results)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (isScanning || bluetoothLeScanner == null) return

        try {
            val scanSettings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val scanFilters = emptyList<android.bluetooth.le.ScanFilter>()

            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            Log.d("Nuix", "BLE scan started")
        } catch (e: SecurityException) {
            Log.e("Nuix", "Bluetooth scan permission denied", e)
        } catch (e: Exception) {
            Log.e("Nuix", "Failed to start BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                Log.d("Nuix", "BLE scan stopped")
            } catch (e: Exception) {
                Log.e("Nuix", "Failed to stop BLE scan", e)
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun cleanup() {
        stopScan()
        discoveredDevices.clear()
        _scanResults.value = emptyList()
    }
}