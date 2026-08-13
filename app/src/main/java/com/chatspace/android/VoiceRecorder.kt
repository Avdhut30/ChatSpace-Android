package com.chatspace.android

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val recording: Boolean get() = recorder != null

    fun start() {
        check(recorder == null)
        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        next.setAudioSource(MediaRecorder.AudioSource.MIC)
        next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        next.setAudioEncodingBitRate(96_000)
        next.setAudioSamplingRate(44_100)
        next.setOutputFile(target.absolutePath)
        next.prepare()
        next.start()
        output = target
        recorder = next
    }

    fun stop(): File? {
        val current = recorder ?: return null
        return try {
            current.stop()
            output?.takeIf { it.length() > 0 }
        } finally {
            current.release()
            recorder = null
            output = null
        }
    }

    fun cancel() { runCatching { recorder?.stop() }; recorder?.release(); recorder = null; output?.delete(); output = null }
}
