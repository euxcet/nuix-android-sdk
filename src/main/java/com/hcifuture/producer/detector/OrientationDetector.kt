package com.hcifuture.producer.detector

import android.content.res.AssetManager
import android.util.Log
import com.hcifuture.producer.detector.utils.MadgwickFilter
import com.hcifuture.producer.detector.utils.Quaternion
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import com.hcifuture.producer.sensor.external.ring.ringV1.RingV1Spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

class OrientationDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    val eventFlow = MutableSharedFlow<Pair<Float, Float>>(replay = 0)
    val filter: MadgwickFilter = MadgwickFilter(0.1f, Quaternion(0.012f, -0.825f, 0.538f, 0.174f), 200.0f)
    private val queue = mutableListOf<Quaternion>()
    private var count = 0

    fun start() {
        scope.launch {
            while (true) {
                Log.e("Nuix", "Ori fps: ${count / 2}")
                count = 0
                delay(2000)
            }
        }
        scope.launch {
            nuixSensorManager.defaultRing.getProxyFlow<RingImuData>(
                RingSpec.imuFlowName(nuixSensorManager.defaultRing)
            )?.collect { imu ->
                count += 1
                filter.update(listOf(
                    imu.data[0], imu.data[1], imu.data[2],
                    imu.data[3], imu.data[4], imu.data[5],
                ))
                val orientation = filter.q.copy()
                queue.add(orientation)
                if (queue.size >= 2) {
                    val delta = queue[0].inv() * orientation
                    val eulerDelta = delta.yawPitchRoll()
                    val moveX = eulerDelta.x * 1200
                    val moveY = -eulerDelta.y * 800
                    if (sqrt(moveX * moveX + moveY * moveY) > 1) {
                        eventFlow.emit(Pair(moveX, moveY))
                    }
                    queue.clear()
                    queue.add(orientation)
                }
            }
        }
    }
}
