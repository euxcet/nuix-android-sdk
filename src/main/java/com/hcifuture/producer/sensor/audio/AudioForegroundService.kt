package com.hcifuture.producer.sensor.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hcifuture.producer.IAudioService
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AudioForegroundService : Service() {

    val NOTIFICATION_ID = 321
    val CHANNEL_ID = "AudioChannel"
    val CHANNEL_NAME = "AudioChannel"
    val TAG = "AudioForegroundService"

    private var mNotificationManager: NotificationManager? = null
    private val mainHandler: Handler by lazy {
        Handler(mainLooper)
    }

    private var audioRecordProcessor: AudioRecordProcessor? = null

    override fun onCreate() {
        super.onCreate()
        audioRecordProcessor = AudioRecordProcessor(this)
    }

    override fun onBind(intent: Intent?): IBinder {

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
        } )

        return object: IAudioService.Stub() {

            @SuppressLint("MissingPermission")
            override fun startRecord(savedFile: String?) {
                mainHandler.post {
                    if (savedFile != null) {
                        audioRecordProcessor?.startRecord(File(savedFile))
                    }
                }
            }

            override fun stopRecord() {
                mainHandler.post {
                    audioRecordProcessor?.stopRecord()
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val notificationChannel =
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)
        return NotificationCompat.Builder(this, CHANNEL_ID).build()
    }

//    private fun createNotification(title: String, content: String): Notification {
//        if (mNotificationManager == null) {
//            mNotificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
//        }
//        val notification: Notification =
//            NotificationCompat.Builder(this, "NORMAL_SERVICE")
//                .setContentTitle(title)
//                .setContentText(content)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .build()
//        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
//        return notification
//    }

    private fun fileTimeFormat(time: LocalDateTime): String {
        val myDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        return time.format(myDateTimeFormatter)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecordProcessor?.stopRecord()
    }
}