package app.nimble.engine

/** Raw JNI surface of libnimble.so (see app/src/main/cpp/nimble_jni.c). Call only from [CactusEngine]. */
object CactusNative {
    /** Null when the native library loaded; otherwise the reason it did not. */
    val loadFailure: String? = try {
        System.loadLibrary("nimble")
        null
    } catch (t: Throwable) {
        t.message ?: t.toString()
    }

    @JvmStatic external fun loadModel(path: String): Int
    @JvmStatic external fun lastError(): ByteArray?
    @JvmStatic external fun init(system: ByteArray, tools: ByteArray): Int
    @JvmStatic external fun complete(text: ByteArray, maxNewTokens: Int): ByteArray?
    @JvmStatic external fun reset()
    @JvmStatic external fun setThreads(threads: Int)
    @JvmStatic external fun transcribe(samples: FloatArray, language: ByteArray?): ByteArray?
}
