package com.hcifuture.producer.detector

import android.content.res.AssetManager
import android.util.Log
import androidx.collection.FloatList
import com.hcifuture.producer.common.network.bean.CharacterResult
import com.hcifuture.producer.common.network.http.HttpService
import com.hcifuture.producer.detector.utils.MadgwickFilter
import com.hcifuture.producer.detector.utils.Quaternion
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.data.RingImuData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Tensor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.sqrt

enum class TouchState {
    UP, DOWN
}

class WordDetector @Inject constructor(
    private val assetManager: AssetManager,
    private val nuixSensorManager: NuixSensorManager,
    private val httpService: HttpService,
) {
    companion object {
        const val GRAVITY = 9.76f
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val labels = arrayOf("ALWAYS_CONTACT", "ALWAYS_NO_CONTACT", "UP", "DOWN")
    private val data = Array(6) { FloatArray(20) { 0.0f } }
    private val moveData = Array(13) { FloatArray(6) { 0.0f } }
    private val accXData = FloatArray(50)
    private var touchState = TouchState.UP
    val filter: MadgwickFilter = MadgwickFilter(0.1f, Quaternion(0.012f, -0.825f, 0.538f, 0.174f), 200.0f)
    private var orientation = filter.q.copy()
    private val trajectory = mutableListOf<List<Float>>()

    val gestureFlow = MutableSharedFlow<TouchState>(replay = 0)
    val moveFlow = MutableSharedFlow<Pair<Float, Float>>(replay = 0)
    val characterFlow = MutableSharedFlow<CharacterResult>(replay = 0)

    var positionX = 0.0f
    var positionY = 0.0f
    var firstTimestamp = 0L
    var counter = 0

    fun getTouchEvent(state: TouchState): TouchState? {
        val event: TouchState? =
            if (touchState == TouchState.UP && state == TouchState.DOWN) {
                TouchState.DOWN
            } else if (touchState == TouchState.DOWN && state == TouchState.UP) {
                TouchState.UP
            } else {
                null
            }
        touchState = state
        return event
    }

    fun isStable(x: List<Float>): Boolean {
        return x.max() - x.min() < 3
    }

    fun detectUp(x: FloatArray): Int {
        if (isStable(x.take(10)) &&
            isStable(x.takeLast(10)) &&
            x.takeLast(10).average() - x.take(10).average() > 4) {
            return 2
        }
        return 0
    }

    fun start() {
        val model = LiteModuleLoader.loadModuleFromAsset(assetManager, "touch_event.ptl")
        val moveModel = LiteModuleLoader.loadModuleFromAsset(assetManager, "move.ptl")
        var hx0 = IValue.from(Tensor.fromBlob(
            FloatArray(128),
            longArrayOf(1, 1, 128)
        ))
        var hx1 = IValue.from(Tensor.fromBlob(
            FloatArray(128),
            longArrayOf(1, 1, 128)
        ))
        scope.launch {
            nuixSensorManager.defaultRing.getProxyFlow<RingImuData>(
                RingSpec.imuFlowName(nuixSensorManager.defaultRing)
            )?.collect { imu ->
                for (i in 0 until 6) {
                    for (j in 0 until 19) {
                        data[i][j] = data[i][j + 1]
                    }
                    data[i][19] = imu.data[i]
                }
                for (i in 0 until 49) {
                    accXData[i] = accXData[i + 1]
                }
                accXData[49] = imu.data[0]
                filter.update(
                    listOf(
                        imu.data[0], imu.data[1], imu.data[2],
                        imu.data[3], imu.data[4], imu.data[5],
                    )
                )
                orientation = filter.q.copy()
                for (i in 0 until 6) {
                    for (j in 0 until 12) {
                        moveData[j][i] = moveData[j + 1][i]
                    }
                }
                moveData[12][0] =
                    imu.data[0] - (2.0f * (orientation.x * orientation.z - orientation.w * orientation.y) * GRAVITY)
                moveData[12][1] =
                    imu.data[1] - (2.0f * (orientation.y * orientation.z + orientation.w * orientation.x) * GRAVITY)
                moveData[12][2] =
                    imu.data[2] - ((orientation.w * orientation.w - orientation.x * orientation.x - orientation.y * orientation.y + orientation.z * orientation.z) * GRAVITY)
                moveData[12][3] = imu.data[3]
                moveData[12][4] = imu.data[4]
                moveData[12][5] = imu.data[5]
                counter += 1
                if (counter % 1 == 0) {
                    val tensor = Tensor.fromBlob(
                        data.flatMap { it.toList() }.toFloatArray(),
                        longArrayOf(1, 6, 20)
                    )
                    val output = model.forward(IValue.from(tensor)).toTensor().dataAsFloatArray
                    val expSum = output.map { exp(it) }.sum()
                    val softmax = output.map { exp(it) / expSum }
                    var result = softmax.withIndex().maxByOrNull { it.value }?.index!!
                    if (softmax[result] < 0.8) {
                        result = 0
                    }
                    if (result != 3) {
                        result = detectUp(accXData)
                    }
                    val event = if (result >= 2) {
                        getTouchEvent(if (result == 2) TouchState.UP else TouchState.DOWN)
                    } else {
                        null
                    }
                    if (event != null) {
                        gestureFlow.emit(event)
                        if (event == TouchState.UP) {
                            val path = trajectory.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                            httpService.detectCharacter(path).enqueue(object: Callback<CharacterResult> {
                                override fun onResponse(
                                    call: Call<CharacterResult>,
                                    response: Response<CharacterResult>
                                ) {
                                    response.body()?.let {
                                        scope.launch {
                                            characterFlow.emit(it)
                                        }
                                    }
                                }

                                override fun onFailure(call: Call<CharacterResult>, t: Throwable) {
                                }
                            })
                            trajectory.clear()
                            positionX = 0.0f
                            positionY = 0.0f
                            firstTimestamp = 0L
                        }
                    }
                }
                if (touchState == TouchState.DOWN) {
                    val inputTensor = Tensor.fromBlob(
                        moveData.flatMap { it.toList() }.toFloatArray(),
                        longArrayOf(1, 13, 6)
                    )
                    val outputTensor = moveModel.forward(
                        IValue.from(inputTensor),
                        IValue.tupleFrom(hx0, hx1),
                    ).toTuple()
                    val output = outputTensor[0].toTensor().dataAsFloatArray
                    val hx = outputTensor[1].toTuple()
                    hx0 = hx[0]
                    hx1 = hx[1]
                    val gyrX = moveData[12][3]
                    val gyrY = moveData[12][4]
                    val gyrZ = moveData[12][5]
                    var deltaX = output[0]
                    var deltaY = output[1]
                    if (sqrt(gyrX * gyrX + gyrY * gyrY + gyrZ * gyrZ) < 0.1f) {
                        deltaX = 0.0f
                        deltaY = 0.0f
                    }
                    moveFlow.emit(Pair(deltaX, deltaY))
                    positionX += deltaX
                    positionY += deltaY
                    val currentTimestamp = System.currentTimeMillis()
                    if (firstTimestamp == 0L) {
                        firstTimestamp = currentTimestamp
                    }
                    trajectory.add(listOf(positionX, positionY, (currentTimestamp - firstTimestamp) * 1.0f))
                }
            }
        }
    }

}
