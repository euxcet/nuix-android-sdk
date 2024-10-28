package com.hcifuture.producer.sensor.video

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.view.Surface
import androidx.camera.core.CameraSelector
import com.hcifuture.producer.IVideoService
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.VideoCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.NuixSensorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VideoSensor(val context: Context): NuixSensor() {
    private var isBindService = false
    private var mVideoServiceRemote: IVideoService? = null
    private var scope = CoroutineScope(Dispatchers.Default)
    private var scopeIO = CoroutineScope(Dispatchers.IO)
    private var currentCameraLens: Int = CameraSelector.LENS_FACING_BACK

    private lateinit var videoServiceConnection: ServiceConnection

    override var name: String = "Camera"

    override val flows: Map<String, StateFlow<Any>> = mapOf(
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow()
    )

    override val defaultCollectors: Map<String, Collector> = mapOf<String, Collector>(
        VideoSensorSpec.videoFlowName(this) to
                VideoCollector(listOf(this), listOf(), "${name}.mp4")
    )

    @Deprecated("Use connect(context: Context) instead")
    override fun connect() {}

    fun connect(
        cameraLens: Int,
        previewSurface: Surface?,
        width: Int,
        height: Int,
    ) {
        if (status !in listOf(NuixSensorState.SCANNING, NuixSensorState.DISCONNECTED)) {
            return
        }
        status = NuixSensorState.CONNECTING
        /**
         * Bind the camera service.
         */
        videoServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?) {
                mVideoServiceRemote = IVideoService.Stub.asInterface(service)
                mVideoServiceRemote?.bindCamera(cameraLens, previewSurface, width, height)
                status = NuixSensorState.CONNECTED
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mVideoServiceRemote = null
                status = NuixSensorState.DISCONNECTED
            }
        }
        val intent = Intent(context, VideoForegroundService::class.java)
        context.bindService(intent, videoServiceConnection, Context.BIND_AUTO_CREATE)
        isBindService = true
        currentCameraLens = cameraLens
    }


    override fun disconnect() {
        if (status != NuixSensorState.CONNECTED) {
            return
        }
        if (mVideoServiceRemote != null) {
            isBindService = false
            try {
                context.unbindService(videoServiceConnection)
            } catch (ignore: Exception) {
            }
            status = NuixSensorState.DISCONNECTED
        }
    }

    fun switchCameraLens(cameraLens: Int) {
        if (status == NuixSensorState.CONNECTED) {
            mVideoServiceRemote?.switchCameraLens(cameraLens)
            currentCameraLens = cameraLens
        }
    }

    fun switchToAnotherCameraLens() {
        val cameraLens = if (currentCameraLens == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        switchCameraLens(cameraLens)
    }

    fun startRecord(savedFile: File, withAudio: Boolean) {
        if (status == NuixSensorState.CONNECTED) {
            mVideoServiceRemote?.startRecord(savedFile.absolutePath, withAudio)
        }
    }

    fun stopRecord() {
        if (status == NuixSensorState.CONNECTED) {
            mVideoServiceRemote?.stopRecord()
        }
    }
}