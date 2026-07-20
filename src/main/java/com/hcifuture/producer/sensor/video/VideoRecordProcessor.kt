package com.hcifuture.producer.sensor.video

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor

class VideoRecordProcessor(
    private val context: Context,
    surface: Surface?,
    executor: Executor
): LifecycleOwner,  Preview.SurfaceProvider {

    companion object {
        const val STATE_INITIALIZING = 0
        const val STATE_READY = 1
        const val STATE_STARTING = 2
        const val STATE_RECORDING = 3
        const val STATE_STOPPING = 4
        const val STATE_FINALIZED = 5
        const val STATE_FAILED = -1
    }

    val TAG = "CameraRecordProcessor"

    override val lifecycle: Lifecycle get() {
        return lifecycleRegistry
    }

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private var mSurface: Surface? = surface
    private val recordExecutor = executor
    private var isBindCamera = false
    private var recordSaveFile: File? = null
    private var recordAudio: Boolean = true
    private val useCases = mutableListOf<UseCase>()
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraLens: Int = CameraSelector.LENS_FACING_BACK

    private val cameraPreview: Preview by lazy {
        val resolutionSelector= ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
        Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
            it.setSurfaceProvider(this)
        }
    }

    private var isRecordStart = false
    private var videoCapture: VideoCapture<Recorder>? = null

    private var currentRecording: Recording? = null
    @Volatile
    var videoState: Int = STATE_INITIALIZING
        private set

    fun setSurface(surface: Surface?) {
        if (mSurface != surface) {
            mSurface = surface
            cameraPreview.setSurfaceProvider(this)
            cameraProvider?.let {
                it.unbind(cameraPreview)
                useCases.remove(cameraPreview)
            }
            updatePreview()
        }
    }

    @SuppressLint("MissingPermission")
    fun bindCamera(cameraProvider: ProcessCameraProvider?, cameraLens: Int) {
        if (isBindCamera) {
            return
        }
        isBindCamera = true
        transToState(Lifecycle.State.CREATED)
        transToState(Lifecycle.State.RESUMED)
        this.cameraProvider = cameraProvider
        this.cameraLens = cameraLens
        updatePreview()
        updateVideoCapture()
        if (!isRecordStart) {
            videoState = STATE_READY
        }
    }

    fun unbindCamera() {
        transToState(Lifecycle.State.CREATED)
        stopRecord()
        cameraProvider?.unbindAll()
        isBindCamera = false
    }

    private fun getCameraSelector(lens: Int): CameraSelector {
        return when(lens) {
            CameraSelector.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun updatePreview() {
        val cameraSelector = getCameraSelector(cameraLens)
        if (mSurface != null) {
            if (!useCases.contains(cameraPreview)) {
                useCases.add(cameraPreview)
            }
        } else {
            useCases.remove(cameraPreview)
            cameraProvider?.unbind(cameraPreview)
        }
        cameraProvider?.bindToLifecycle(this, cameraSelector, *useCases.toTypedArray())
    }

    private fun updateVideoCapture() {
        val cameraSelector = getCameraSelector(cameraLens)
        if (videoCapture != null && useCases.contains(videoCapture!!)) {
            videoCapture?.let {
                useCases.remove(it)
                cameraProvider?.unbind(it)
            }
        }
        if (isRecordStart) {
            initVideoCapture()
        }
        cameraProvider?.bindToLifecycle(this, cameraSelector, *useCases.toTypedArray())
        if (isRecordStart) {
            beginVideoRecording()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initVideoCapture() {
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.LOWEST),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST))
        val recorder: Recorder = Recorder.Builder().setQualitySelector(qualitySelector).setAspectRatio(
            AspectRatio.RATIO_16_9
        ).build()
        videoCapture = VideoCapture.withOutput(recorder)
        useCases.add(videoCapture!!)
    }

    @SuppressLint("MissingPermission")
    private fun beginVideoRecording() {
        val capture = videoCapture ?: run {
            videoState = STATE_FAILED
            return
        }
        val outputOptions = FileOutputOptions.Builder(recordSaveFile!!).build()
        val pendingRecord = capture.output.prepareRecording(context, outputOptions).apply {
            if (recordAudio) {
                withAudioEnabled()
            }
        }
        videoState = STATE_STARTING
        currentRecording = pendingRecord.start(recordExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> videoState = STATE_RECORDING
                is VideoRecordEvent.Finalize -> {
                    videoState = if (event.hasError()) STATE_FAILED else STATE_FINALIZED
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecord(file: File, withAudio: Boolean) {
        recordSaveFile = file
        recordAudio = withAudio
        isRecordStart = true
        updateVideoCapture()
    }

    fun stopRecord() {
        if (videoState == STATE_RECORDING || videoState == STATE_STARTING) {
            videoState = STATE_STOPPING
            currentRecording?.stop()
        } else if (currentRecording == null) {
            videoState = STATE_FAILED
        }
        isRecordStart = false
    }

    fun changeCameraLens(cameraLens: Int) {
        this.cameraLens = cameraLens
        cameraProvider?.unbindAll()
        updateVideoCapture()
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        mSurface?.let { surface ->
            request.provideSurface(surface, recordExecutor) {
                Log.d(TAG, "onSurfaceRequestedResult: ${it.resultCode}")
                if (it.resultCode == SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY) {
                    Log.d(TAG, "provideSurface success")
                }
            }
        }
    }

    private fun transToState(state: Lifecycle.State) {
        try {
            lifecycleRegistry.currentState = state
        } catch (ignore: Exception) {

        }
    }

    private fun fileTimeFormat(time: LocalDateTime): String {
        val myDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        return time.format(myDateTimeFormatter)
    }
}
