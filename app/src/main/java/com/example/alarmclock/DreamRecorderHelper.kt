package com.example.alarmclock

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class DreamRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File? {
        return try {
            val dir = File(context.filesDir, "dreams").apply { mkdirs() }
            outputFile = File(dir, "dream_${System.currentTimeMillis()}.m4a")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            null
        }
    }

    fun isRecording() = recorder != null
}
