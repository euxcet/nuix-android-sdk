package com.hcifuture.producer.sensor.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecordProcessor(
    private val context: Context
) {

    private var record: MediaRecorder? = null

    private fun createMediaRecorder(savedFile: File): MediaRecorder {
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        Log.e("NUIX", "[AUDIO] setup media recorder ${Build.VERSION.SDK_INT}")
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(savedFile.absolutePath)
        }
        return mediaRecorder
    }

    fun startRecord(savedFile: File) {
        Log.e("NUIX", "[AUDIO] start record")
        if (record != null) {
            record?.stop()
            record?.release()
        }
        record = createMediaRecorder(savedFile).apply {
            prepare()
            start()
        }
    }

    fun stopRecord() {
        Log.e("NUIX", "[AUDIO] stop record")
        record?.apply {
            stop()
            release()
        }
        record = null
    }
}