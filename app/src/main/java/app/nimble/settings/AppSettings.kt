package app.nimble.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User settings, persisted in SharedPreferences and observable from Compose. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nimble", Context.MODE_PRIVATE)

    val cores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private var _threads by mutableIntStateOf(prefs.getInt(KEY_THREADS, minOf(4, cores)).coerceIn(1, cores))
    /** CPU threads the engine uses. Set manually in Settings. */
    var threads: Int
        get() = _threads
        set(value) {
            _threads = value.coerceIn(1, cores)
            prefs.edit().putInt(KEY_THREADS, _threads).apply()
        }

    private var _confidence by mutableFloatStateOf(prefs.getFloat(KEY_CONFIDENCE, 0.6f))
    /** Below this confidence Nimble asks before acting. */
    var confidence: Float
        get() = _confidence
        set(value) {
            _confidence = value.coerceIn(0f, 1f)
            prefs.edit().putFloat(KEY_CONFIDENCE, _confidence).apply()
        }

    private var _voiceLanguage by mutableStateOf(prefs.getString(KEY_LANGUAGE, AUTO) ?: AUTO)
    /** Whistle language code, or [AUTO] to detect it. */
    var voiceLanguage: String
        get() = _voiceLanguage
        set(value) {
            _voiceLanguage = value
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    companion object {
        const val AUTO = "auto"
        val LANGUAGES = linkedMapOf(
            AUTO to "Detect automatically",
            "en" to "English",
            "de" to "German",
            "fr" to "French",
            "es" to "Spanish",
            "it" to "Italian",
            "nl" to "Dutch",
            "pl" to "Polish",
        )
        private const val KEY_THREADS = "threads"
        private const val KEY_CONFIDENCE = "confidence"
        private const val KEY_LANGUAGE = "voice_language"
    }
}
