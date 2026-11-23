package app.nimble.models

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState {
        val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data class Ready(val bytes: Long) : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Downloads weights from Hugging Face into app storage and verifies their SHA-256.
 * This is the only network use in the app; inference is fully on-device.
 */
class ModelManager(context: Context) {
    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<ModelSpec, Job>()

    private val _states = MutableStateFlow(ModelSpec.entries.associateWith { stateOnDisk(it) })
    val states: StateFlow<Map<ModelSpec, ModelState>> = _states.asStateFlow()

    fun file(spec: ModelSpec) = File(dir, spec.fileName)

    fun isReady(spec: ModelSpec) = file(spec).isFile

    fun download(spec: ModelSpec) {
        if (jobs[spec]?.isActive == true) return
        jobs[spec] = scope.launch {
            set(spec, ModelState.Downloading(0, 0))
            try {
                fetch(spec)
                set(spec, stateOnDisk(spec))
            } catch (e: CancellationException) {
                set(spec, stateOnDisk(spec))
                throw e
            } catch (e: Exception) {
                set(spec, ModelState.Failed(e.message ?: e.toString()))
            }
        }
    }

    fun cancel(spec: ModelSpec) {
        jobs.remove(spec)?.cancel()
    }

    fun delete(spec: ModelSpec) {
        cancel(spec)
        file(spec).delete()
        set(spec, ModelState.Missing)
    }

    private suspend fun fetch(spec: ModelSpec) {
        val part = File(dir, spec.fileName + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Nimble-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code while downloading ${spec.fileName}")
            val total = connection.contentLengthLong
            var bytes = 0L
            var lastReport = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        bytes += n
                        if (bytes - lastReport > 256 * 1024) {
                            lastReport = bytes
                            set(spec, ModelState.Downloading(bytes, total))
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != spec.sha256) throw IOException("Checksum mismatch for ${spec.fileName}")
            if (!part.renameTo(file(spec))) throw IOException("Could not save ${spec.fileName}")
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    private fun stateOnDisk(spec: ModelSpec): ModelState {
        val f = file(spec)
        return if (f.isFile) ModelState.Ready(f.length()) else ModelState.Missing
    }

    private fun set(spec: ModelSpec, state: ModelState) {
        _states.update { it + (spec to state) }
    }
}
