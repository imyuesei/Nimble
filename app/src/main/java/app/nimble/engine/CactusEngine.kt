package app.nimble.engine

import app.nimble.models.ModelManager
import app.nimble.models.ModelSpec
import app.nimble.settings.AppSettings
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

class EngineException(message: String) : Exception(message)

/**
 * The single owner of the native engine. Cactus holds one global Needle session, so every
 * call is funnelled through one thread, and the currently initialised toolset is tracked
 * here to skip redundant `cactus_init` calls.
 */
class CactusEngine(
    private val models: ModelManager,
    private val settings: AppSettings,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "cactus-engine").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val loaded = mutableSetOf<ModelSpec>()
    private var activeSession: String? = null
    private var appliedThreads = -1

    /** Starts a fresh Needle conversation bound to [toolsJson] and sends [text]. */
    suspend fun complete(system: String, toolsJson: String, text: String, maxNewTokens: Int = 256): JSONObject =
        withContext(dispatcher) {
            try {
                completeLocked(system, toolsJson, text, maxNewTokens, fresh = true)
            } catch (e: EngineException) {
                // Loading another model (e.g. Whistle) may have invalidated the session: retry once.
                activeSession = null
                completeLocked(system, toolsJson, text, maxNewTokens, fresh = true)
            }
        }

    /** Continues the current conversation, e.g. by feeding tool results back. */
    suspend fun continueWith(text: String, maxNewTokens: Int = 256): JSONObject = withContext(dispatcher) {
        val out = CactusNative.complete(text.cBytes(), maxNewTokens) ?: throw EngineException(lastError())
        JSONObject(String(out, Charsets.UTF_8))
    }

    /** Speech to text with Whistle. Samples are 16 kHz mono floats in [-1, 1]. */
    suspend fun transcribe(samples: FloatArray, language: String?): JSONObject = withContext(dispatcher) {
        prepare(ModelSpec.WHISTLE)
        val out = CactusNative.transcribe(samples, language?.cBytes())
            ?: throw EngineException(lastError())
        JSONObject(String(out, Charsets.UTF_8))
    }

    /** Forget a model (after it was deleted); it is reloaded from disk on next use. */
    suspend fun invalidate(spec: ModelSpec) = withContext(dispatcher) {
        loaded.remove(spec)
        if (spec == ModelSpec.NEEDLE) activeSession = null
    }

    private fun completeLocked(
        system: String, toolsJson: String, text: String, maxNewTokens: Int, fresh: Boolean,
    ): JSONObject {
        prepare(ModelSpec.NEEDLE)
        val key = system + "\u0000" + toolsJson
        if (key != activeSession) {
            if (CactusNative.init(system.cBytes(), toolsJson.cBytes()) < 0) {
                activeSession = null
                throw EngineException(lastError())
            }
            activeSession = key
        } else if (fresh) {
            CactusNative.reset()
        }
        val out = CactusNative.complete(text.cBytes(), maxNewTokens) ?: throw EngineException(lastError())
        return JSONObject(String(out, Charsets.UTF_8))
    }

    private fun prepare(spec: ModelSpec) {
        CactusNative.loadFailure?.let { throw EngineException("Native engine failed to load: $it") }
        val threads = settings.threads
        if (threads != appliedThreads) {
            CactusNative.setThreads(threads)
            appliedThreads = threads
            // The thread count applies to models loaded after it is set, so reload them.
            loaded.clear()
            activeSession = null
        }
        if (spec in loaded) return
        val file = models.file(spec)
        if (!file.isFile) throw EngineException("${spec.title} is not downloaded. Open Settings to download it.")
        if (CactusNative.loadModel(file.absolutePath) < 0) {
            throw EngineException("Could not load ${spec.title}: ${lastError()}")
        }
        loaded += spec
        if (spec == ModelSpec.NEEDLE) activeSession = null
    }

    private fun lastError(): String =
        CactusNative.lastError()?.let { String(it, Charsets.UTF_8) }?.ifBlank { null } ?: "engine error"

    private fun String.cBytes(): ByteArray = toByteArray(Charsets.UTF_8)
}
