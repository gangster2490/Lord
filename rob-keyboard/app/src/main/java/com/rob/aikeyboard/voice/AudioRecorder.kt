package com.rob.aikeyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Records 16 kHz mono speech into memory and returns it as a WAV file, for cloud transcription.
 * Nothing is written to disk. Stops by itself after [MAX_SECONDS].
 */
class AudioRecorder(private val onLevel: (Float) -> Unit, private val onLimit: () -> Unit) {
    @Volatile private var recording = false
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    val isRecording: Boolean get() = recording

    /** Caller must hold RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        }.getOrNull() ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return false }
        synchronized(pcm) { pcm.reset() }
        recording = true
        record.startRecording()
        thread = Thread {
            val buf = ShortArray(minBuf)
            val bytes = ByteBuffer.allocate(minBuf * 2).order(ByteOrder.LITTLE_ENDIAN)
            var total = 0L
            while (recording) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                bytes.clear()
                var sum = 0.0
                for (i in 0 until n) { bytes.putShort(buf[i]); sum += buf[i].toDouble() * buf[i] }
                synchronized(pcm) { pcm.write(bytes.array(), 0, n * 2) }
                val rms = sqrt(sum / n)
                onLevel(if (rms > 1) (20 * log10(rms / 3000)).toFloat().coerceIn(-2f, 10f) + 2 else 0f)
                total += n
                if (total >= RATE * MAX_SECONDS) { recording = false; onLimit() }
            }
            record.stop()
            record.release()
        }.apply { start() }
        return true
    }

    /** Stops and returns the recording as WAV bytes (empty if nothing was captured). */
    fun stop(): ByteArray {
        recording = false
        thread?.join(1000)
        thread = null
        val data = synchronized(pcm) { pcm.toByteArray().also { pcm.reset() } }
        return if (data.isEmpty()) data else wav(data)
    }

    fun cancel() {
        recording = false
        thread?.join(1000)
        thread = null
        synchronized(pcm) { pcm.reset() }
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    companion object {
        const val RATE = 16_000
        const val MAX_SECONDS = 120
        /** Under half a second of audio is treated as an accidental tap. */
        const val MIN_BYTES = 44 + RATE
    }
}
