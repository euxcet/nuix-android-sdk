package com.hcifuture.producer.sensor.video

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.view.Surface
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hcifuture.producer.IVideoService
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class VideoForegroundService : Service() {

    val NOTIFICATION_ID = 320
    val TAG = "CameraForegroundService"

    private var mNotificationManager: NotificationManager? = null
    private val mainHandler: Handler by lazy {
        Handler(mainLooper)
    }

    private var videoProcessor: VideoRecordProcessor? = null

    private var videoProvider: ProcessCameraProvider? = null

    private val recordExecutor: Executor by lazy {
        ThreadPoolExecutor(2, 3, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    }

    @RequiresPermission("android.permission.RECORD_AUDIO")
    private fun prepareCamera(cameraLens: Int = CameraSelector.LENS_FACING_BACK) {
        if (videoProvider != null) {
            bindCamera(cameraLens)
            return
        }
        ProcessCameraProvider.getInstance(this).addListener({
            videoProvider = ProcessCameraProvider.getInstance(this).get()
            bindCamera(cameraLens)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(cameraLens: Int = CameraSelector.LENS_FACING_BACK) {
        videoProvider?.let {
            videoProcessor?.bindCamera(it, cameraLens)
        }
    }

    private fun unbindCamera() {
        videoProcessor?.unbindCamera()
    }

    override fun onBind(intent: Intent?): IBinder {
        startForeground(NOTIFICATION_ID, createNotification("Assistant", "正在使用摄像头"))
        return object: IVideoService.Stub() {

            @SuppressLint("MissingPermission")
            override fun bindCamera(
                cameraLens: Int,
                previewSurface: Surface?,
                width: Int,
                height: Int
            ) {
                mainHandler.post {
                    if (videoProcessor == null) {
                        videoProcessor = VideoRecordProcessor(
                            this@VideoForegroundService,
                            previewSurface,
                            recordExecutor,
                        )
                    }
                    prepareCamera(cameraLens)
                }
            }

            override fun switchCameraLens(cameraLens: Int) {
                mainHandler.post {
                    videoProcessor?.changeCameraLens(cameraLens)
                }
            }

            @SuppressLint("MissingPermission")
            override fun startRecord(savedFile: String?, withAudio: Boolean) {
                mainHandler.post {
                    videoProcessor?.startRecord(File(savedFile!!), withAudio)
                }
            }

            override fun updatePreview(previewSurface: Surface?) {
                mainHandler.post {
                    videoProcessor?.setSurface(previewSurface)
                }
            }

            override fun stopRecord() {
                mainHandler.post {
                    videoProcessor?.stopRecord()
                }
            }

            override fun unbindCamera() {
                mainHandler.post {
                    videoProcessor?.unbindCamera()
                }
            }

        }
    }

    private fun createNotification(title: String, content: String): Notification {
        if (mNotificationManager == null) {
            mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        }
        val notification: Notification =
            NotificationCompat.Builder(this, "NORMAL_SERVICE")
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        return notification
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindCamera()
    }
}