package com.hcifuture.producer.detector

import android.content.res.AssetManager
import android.util.Log
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.external.ring.RingSpec
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

class GestureDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    val eventFlow = MutableSharedFlow<String>(replay = 0)
    private val labels = arrayOf("none", "wave_right", "wave_down", "wave_left", "wave_up",
        "tap_air", "tap_plane", "push_forward", "pinch", "clench", "flip", "wrist_clockwise",
        "wrist_counterclockwise", "circle_clockwise", "circle_counterclockwise", "clap", "snap",
        "thumb_up", "middle_pinch", "index_flick", "touch_plane", "thumb_tap_index",
        "index_bend_and_straighten", "ring_pinch", "pinky_pinch", "slide_plane",
        "pinch_down", "pinch_up", "boom", "tap_up", "throw", "touch_left", "touch_right",
        "slide_up", "slide_down", "slide_left", "slide_right", "aid_slide_left", "aid_slide_right",
        "touch_up", "touch_down", "touch_ring", "long_touch_ring", "spread_ring")
    private val calculateFrequency: Float = 50.0f

    private val data = Array(6) { FloatArray(200) { 0.0f } }
    private var pinchDown = false

    fun start() {
        scope.launch {
            nuixSensorManager.defaultRing.getProxyFlow<RingImuData>(
                RingSpec.imuFlowName(nuixSensorManager.defaultRing)
            )?.collect { imu ->
                for (i in 0 until 6) {
                    for (j in 0 until 199) {
                        data[i][j] = data[i][j + 1]
                    }
                    data[i][199] = imu.data[i]
                }
            }
        }

        scope.launch {
            val model = LiteModuleLoader.loadModuleFromAsset(assetManager, "gesture.ptl")
            while (true) {
                delay((1000.0f / calculateFrequency).toLong())
                val tensor = Tensor.fromBlob(
                    data.flatMap { it.toList() }.toFloatArray(),
                    longArrayOf(1, 6, 200)
                )
                val output = model.forward(IValue.from(tensor)).toTensor().dataAsFloatArray
                val expSum = output.map { exp(it) }.sum()
                val softmax = output.map { exp(it) / expSum }
                val result = softmax.withIndex().maxByOrNull { it.value }?.index!!
                if (result != 0 && softmax[result] > 0.9) {
                    if (labels[result] == "pinch_down") {
                        pinchDown = true
                    } else if (labels[result] in arrayOf("pinch", "pinch_up")) {
                        pinchDown = false
                    }
                    if (labels[result] in arrayOf("pinch", "middle_pinch", "clap", "snap",
                            "tap_plane", "tap_air", "circle_clockwise", "circle_counterclockwise",
                            "touch_ring", "touch_up", "touch_down")) {
                        eventFlow.emit(labels[result])
                        for (i in 0 until 6) {
                            for (j in 0 until 199) {
                                data[i][j] = data[i][199]
                            }
                        }
                    }
                }
            }
        }
    }
}
