package app.nimble.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Records 16 kHz mono float PCM, the format Whistle expects. */
class VoiceRecorder {
    private var record: AudioRecord? = null
    private var job: Job? = null
    private val chunks = ArrayList<FloatArray>()
    @Volatile private var sampleCount = 0

    val seconds: Int get() = sampleCount / SAMPLE_RATE

    /** Caller must hold RECORD_AUDIO. Returns false when the microphone is unavailable. */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        cancel()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return false
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, SAMPLE_RATE * 4),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        synchronized(chunks) { chunks.clear() }
        sampleCount = 0
        rec.startRecording()
        record = rec
        // The reading coroutine owns the AudioRecord and releases it when it ends, so a
        // read is never in flight on a released recorder.
        job = scope.launch(Dispatchers.IO) {
            val buffer = FloatArray(SAMPLE_RATE / 10)
            try {
                while (isActive) {
                    val n = rec.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (n > 0 && sampleCount < MAX_SAMPLES) {
                        synchronized(chunks) { chunks.add(buffer.copyOf(n)) }
                        sampleCount += n
                    } else if (n < 0) {
                        break
                    }
                }
            } finally {
                rec.release()
            }
        }
        return true
    }

    /** Stops recording and returns the captured audio. */
    suspend fun stop(): FloatArray {
        val rec = record ?: return FloatArray(0)
        try { rec.stop() } catch (_: IllegalStateException) {}
        job?.cancel()
        job?.join()
        record = null
        job = null
        return synchronized(chunks) {
            val out = FloatArray(chunks.sumOf { it.size })
            var at = 0
            for (c in chunks) { c.copyInto(out, at); at += c.size }
            chunks.clear()
            out
        }
    }

    fun cancel() {
        record?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
        }
        job?.cancel()
        record = null
        job = null
        synchronized(chunks) { chunks.clear() }
        sampleCount = 0
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MAX_SAMPLES = SAMPLE_RATE * 60
    }
}
