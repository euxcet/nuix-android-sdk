package com.hcifuture.producer.sensor.external

import android.Manifest
import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleProvider @Inject constructor(
    @ApplicationContext val context: Context
) : NuixSensorProvider {
    override val requireScan: Boolean = true
    override fun get(): List<NuixSensor> {
        return listOf()
    }

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<NuixSensor>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_DENIED) {
            Log.e("Nuix", "No permission for BLE scan")
            return flowOf()
        }
        val aggregator = BleScanResultAggregator()
        Log.e("Nuix", "Scanning")
        return BleScanner(context).scan()
            .filter {
                (it.device.name?:"").startsWith("BCL") ||
                (it.device.name?:"").contains("Ring")
            }
            .map { aggregator.aggregateDevices(it) }
            .map {
                it.map { device ->
                    Log.e("Nuix", "Ring found: ${device.name}, address: ${device.address}")
                    if (device.name?.contains("Ring") == true) {
                        RingV1(context, device.name ?: "RingV1 Unnamed", device.address)
                    } else {
                        RingV2(context, device.name ?: "RingV2 Unnamed", device.address)
                    }
                }
            }
    }
}
