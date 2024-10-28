package com.hcifuture.producer.detector

import android.content.res.AssetManager
import android.util.Log
import com.hcifuture.producer.sensor.data.InternalSensorData
import com.hcifuture.producer.sensor.internal.InternalSensorSpec
import com.hcifuture.producer.sensor.NuixSensorManager
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

// TODO: refactor
class PhoneGestureDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    val eventFlow = MutableSharedFlow<String>()
    private val labels = arrayOf(
        "指向", "敲击", "放置", "靠拢",
        "negative", "point.negative",
        "knock.negative", "place.negative", "lean.negative"
    )
    private val minTriggerInterval = 1000L
    private var lastGesture = -1
    private var lastGestureCount = 0
    private var lastTrigger = 0L
    private var beginSequence = false
    private var gestureSequenceCount = IntArray(9) { 0 }
    private val calculateFrequency: Float = 20.0f
    private val data = Array(6) { FloatArray(375) { 0.0f } }

    fun start() {
        for (sensor in listOf(InternalSensorSpec.accelerometer, InternalSensorSpec.gyroscope)) {
            nuixSensorManager.getSensor(sensor)!!.connect()
        }
        scope.launch {
            nuixSensorManager.getFlow<InternalSensorData>(
                InternalSensorSpec.eventFlowName(InternalSensorSpec.accelerometer)
            )!!
                .collect { acc ->
                    // TODO: lock?
                    for (i in 0 until 3) {
                        data[i].copyInto(data[i], 0, 1, data[i].size)
                        data[i][data[i].size - 1] = acc.data[i]
                    }
                }
        }

        scope.launch {
            nuixSensorManager.getFlow<InternalSensorData>(
                InternalSensorSpec.eventFlowName(InternalSensorSpec.gyroscope)
            )!!
                .collect { gyro ->
                    // TODO: lock?
                    for (i in 3 until 6) {
                        data[i].copyInto(data[i], 0, 1, data[i].size)
                        data[i][data[i].size - 1] = gyro.data[i - 3]
                    }
                }
        }

        scope.launch {
            val model = LiteModuleLoader.loadModuleFromAsset(assetManager, "model.ptl")
            while (true) {
                delay((1000.0f / calculateFrequency).toLong())
                val tensor = Tensor.fromBlob(
                    data.flatMap{ it.toList() }.toFloatArray(),
                    longArrayOf(1, 6, 1, 375)
                )
                val output = model.forward(IValue.from(tensor)).toTensor().dataAsFloatArray
                val expSum = output.map { exp(it) }.sum()
                val softmax = output.map { exp(it) / expSum }
                val result = softmax.withIndex().maxByOrNull { it.value }?.index!!
//                Log.e("Test", "${result} ${softmax[result]}")
                if (softmax[result] > 0.98) {
                    if (result != lastGesture) {
                        lastGesture = result
                        lastGestureCount = 1
                    } else {
                        lastGestureCount += 1
                    }
                }
                val currentTimestamp = System.currentTimeMillis()
                if (softmax[result] > 0.95 &&
                    !labels[result].contains("negative")) {
                    if (!beginSequence) {
                        beginSequence = true
                        gestureSequenceCount = IntArray(9) { 0 }
                    }
                    gestureSequenceCount[result] += 1
                } else {
                    if (beginSequence) {
                        beginSequence = false
                        val maxCountIndex = gestureSequenceCount.withIndex().maxByOrNull { it.value }?.index!!
                        if (gestureSequenceCount[maxCountIndex] > 5 &&
                            currentTimestamp > lastTrigger + minTriggerInterval) {
                            lastTrigger = currentTimestamp
                            Log.e("Test", labels[maxCountIndex])
                            eventFlow.emit(labels[maxCountIndex])
                        }
                    }
                }
                if (currentTimestamp > lastTrigger + 2000) {
                    eventFlow.emit("无")
                }
            }
        }
    }
}
