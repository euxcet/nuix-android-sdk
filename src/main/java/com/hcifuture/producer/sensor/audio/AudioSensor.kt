package com.hcifuture.producer.sensor.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import com.hcifuture.producer.IAudioService
import com.hcifuture.producer.recorder.Collector
import com.hcifuture.producer.recorder.collectors.AudioCollector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorSpec
import com.hcifuture.producer.sensor.NuixSensorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AudioSensor (
    val context: Context,
    override val name: String,
): NuixSensor() {

    private var isBindService = false
    private var mBindContext: Context? = null
    private var mAudioServiceRemote: IAudioService? = null
    private var scope = CoroutineScope(Job() + Dispatchers.Default)
    private lateinit var audioServiceConnection: ServiceConnection

    override val defaultCollectors: Map<String, Collector> = mapOf<String, Collector>(
        AudioSensorSpec.audioFlowName(this) to
                AudioCollector(listOf(this), listOf(), "${name}.mp4")
    )
    override val flows: Map<String, StateFlow<Any>> = mapOf(
        NuixSensorSpec.lifecycleFlowName(this) to lifecycleFlow.asStateFlow(),
    )

    override fun connect() {
        if (!connectable()) return
        status = NuixSensorState.CONNECTING
        audioServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?) {
                mAudioServiceRemote = IAudioService.Stub.asInterface(service)
                status = NuixSensorState.CONNECTED
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                mAudioServiceRemote = null
                status = NuixSensorState.DISCONNECTED
            }
        }

        val intent = Intent(context, AudioForegroundService::class.java)
        context.bindService(intent, audioServiceConnection, Context.BIND_AUTO_CREATE)
        isBindService = true
        mBindContext = context
    }

    override fun disconnect() {
        if (!disconnectable()) return
        if (mAudioServiceRemote != null) {
            isBindService = false
            try {
                mBindContext?.unbindService(audioServiceConnection)
            } catch (ignore: Exception) {
            }
            status = NuixSensorState.DISCONNECTED
        }
    }

    fun startRecord(savedFile: File) {
        if (status == NuixSensorState.CONNECTED) {
            mAudioServiceRemote?.startRecord(savedFile.absolutePath)
        }
    }

    fun stopRecord() {
        if (status == NuixSensorState.CONNECTED) {
            mAudioServiceRemote?.stopRecord()
        }
    }
}