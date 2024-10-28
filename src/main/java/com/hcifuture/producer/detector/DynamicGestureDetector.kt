package com.hcifuture.producer.detector

import android.content.res.AssetManager
import android.util.Log
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import com.hcifuture.producer.sensor.external.ring.ringV1.RingV1Spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Tensor
import javax.inject.Inject
import kotlin.math.exp

class DynamicGestureDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    val eventFlow = MutableSharedFlow<String>(replay = 0)
    private val labels = arrayOf("negative", "negative", "negative", "炸弹", "靠拢",
        "前进", "集合", "赶快", "敌人", "掩护", "重复",
        "negative", "negative", "negative", "negative",
        "negative", "negative", "negative", "negative")
    private var lastTriggerTimestamp = LongArray(20) { 0L }
    private val minTriggerInterval = 500L
    private var lastGesture = -1
    private var lastGestureCount = 0
    private var lastTrigger = 0L
    private val calculateFrequency: Float = 20.0f
    private var gestureOccur = false

    private val data = Array(6) { FloatArray(200) { 0.0f } }
    private var count = 0

    fun start() {
        scope.launch {
            while (true) {
                Log.e("Nuix", "Gesture fps: ${count / 2}")
                count = 0
                delay(2000)
            }
        }

        scope.launch {
            nuixSensorManager.defaultRing.getProxyFlow<RingImuData>(
                RingSpec.imuFlowName(nuixSensorManager.defaultRing)
            )?.collect { imu ->
                for (i in 0 until 6) {
                    data[i].copyInto(data[i], 0, 1, data[i].size)
                    data[i][data[i].size - 1] = imu.data[i]
                }
            }
        }

        scope.launch {
            val model = LiteModuleLoader.loadModuleFromAsset(assetManager, "dynamic.ptl")
            while (true) {
                delay((1000.0f / calculateFrequency).toLong())
                count += 1
                val tensor = Tensor.fromBlob(
                    data.flatMap{ it.toList() }.toFloatArray(),
                    longArrayOf(1, 6, 1, 200)
                )
                val output = model.forward(IValue.from(tensor)).toTensor().dataAsFloatArray
                val expSum = output.map { exp(it) }.sum()
                val softmax = output.map { exp(it) / expSum }
                val result = softmax.withIndex().maxByOrNull { it.value }?.index!!
                val currentTimestamp = System.currentTimeMillis()
                if (softmax[result] > 0.98) {
                    if (result != lastGesture) {
                        lastGesture = result
                        lastGestureCount = 1
                    } else {
                        lastGestureCount += 1
                    }
                }
                if (softmax[result] > 0.98 &&
                    !labels[result].contains("negative") &&
                    lastGestureCount >= 3) {
                    // Log.e("Nuix", labels[result])
                    if (currentTimestamp > lastTrigger + minTriggerInterval &&
                        currentTimestamp > lastTriggerTimestamp[result] + 500L) {
                        eventFlow.emit(labels[result])
                        lastTriggerTimestamp[result] = currentTimestamp
                        lastTrigger = currentTimestamp
                        gestureOccur = true
                    }
                }
                if (currentTimestamp > lastTrigger + 3000 && gestureOccur) {
                    gestureOccur = false
                    eventFlow.emit("")
                }
            }
        }
    }
}
