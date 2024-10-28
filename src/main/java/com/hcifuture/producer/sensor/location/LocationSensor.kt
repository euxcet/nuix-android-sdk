package com.hcifuture.producer.sensor.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.BytesDataCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationSensor(val context: Context) : NuixSensor() {

    private var scope = CoroutineScope(Dispatchers.Default)

    override var name: String = "gps"

    companion object {
        private const val TAG = "LocationSensor"
    }

    private var mLastLocation: Location? = null


    private val _sharedFlow = MutableSharedFlow<LocationData>(1)
    override val flows = mapOf(
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow(),
        LocationSensorSpec.locationFlowName(this) to _sharedFlow.asSharedFlow(),
    )
    override val defaultCollectors: Map<String, Collector> = mapOf(
        LocationSensorSpec.locationFlowName(this) to BytesDataCollector(listOf(this), listOf(_sharedFlow.asSharedFlow()), "${name}.bin")
    )

    private val mLocationManager: LocationManager by lazy {
        context.getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private val mLocationListener: LocationListener = object : LocationListener {
        // Provider的状态在可用、暂时不可用和无服务三个状态直接切换时触发此函数
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Log.d(TAG, "onStatusChanged")
        }

        // Provider被enable时触发此函数，比如GPS被打开
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "onProviderEnabled")
        }

        // Provider被disable时触发此函数，比如GPS被关闭
        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "onProviderDisabled")
        }

        //当坐标改变时触发此函数，如果Provider传进相同的坐标，它就不会被触发
        override fun onLocationChanged(location: Location) {
            scope.launch {
                mLastLocation = location
                _sharedFlow.emit(LocationData(LocationSensorSpec.providerNameToType(location.provider?:""), location.longitude, location.latitude, location.time))
            }

        }
    }

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 判断是否开启了GPS定位开关和网络定位
     */
    fun isLocationProviderEnabled(): Boolean {
        return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(): Location? {
        if (mLastLocation == null) {
            mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        return mLastLocation
    }

    @SuppressLint("MissingPermission")
    override fun connect() {
        if (status !in listOf(NuixSensorState.SCANNING, NuixSensorState.DISCONNECTED)) {
            return
        }
        if (!hasLocationPermission() || !isLocationProviderEnabled()) {
            return
        }
        status = NuixSensorState.CONNECTING
        status = NuixSensorState.CONNECTED
        mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            if (mLastLocation != null && it.time <= mLastLocation!!.time) {
                return@let // 如果新的位置信息不是最新的，就不发送
            }
            mLastLocation = it
            scope.launch {
                _sharedFlow.emit(LocationData(LocationSensorSpec.providerNameToType(mLastLocation?.provider?:""), it.longitude, it.latitude, it.time))
            }
        }
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).onEach {
            if (mLocationManager.isProviderEnabled(it)) {
                mLocationManager.requestLocationUpdates(
                    it,
                    60000,
                    10f,
                    mLocationListener,
                    Looper.getMainLooper()
                )
            }
        }
    }

    override fun disconnect() {
        if (status != NuixSensorState.CONNECTED) {
            return
        }
        status = NuixSensorState.DISCONNECTED
        mLocationManager.removeUpdates(mLocationListener)
    }
}