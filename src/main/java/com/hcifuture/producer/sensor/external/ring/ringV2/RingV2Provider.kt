package com.hcifuture.producer.sensor.external.ring.ringV2

import android.annotation.SuppressLint
import android.content.Context
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("Use BleProvider.")
@Singleton
class RingV2Provider @Inject constructor(
    @ApplicationContext val context: Context
) : NuixSensorProvider {
    override val requireScan: Boolean = true
    override fun get(): List<NuixSensor> {
        return listOf()
    }

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<RingV2>> {
        val aggregator = BleScanResultAggregator()
        return BleScanner(context).scan()
            .filter { (it.device.name?:"").startsWith("BCL") }
            .map { aggregator.aggregateDevices(it) }
            .map {
                it.map { ring ->
                    RingV2(context, ring.name?:"RingV2 Unnamed", ring.address)
                }
            }
    }
}
