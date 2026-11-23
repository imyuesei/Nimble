package app.nimble.ui

import android.Manifest
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nimble.NimbleApp
import app.nimble.harness.PendingCall
import app.nimble.harness.Reply
import app.nimble.models.ModelSpec
import app.nimble.settings.AppSettings
import app.nimble.voice.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Role { USER, ASSISTANT, STATUS }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    /** Set while the message is a yes/no question about a proposed action. */
    val pending: PendingCall? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NimbleApp
    val settings get() = app.settings
    val models get() = app.models

    val messages = mutableStateListOf<ChatMessage>()
    var busy by mutableStateOf(false)
        private set
    var recording by mutableStateOf(false)
        private set
    var recordSeconds by mutableIntStateOf(0)
        private set

    private val recorder = VoiceRecorder()
    private var nextId = 0L

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return
        add(Role.USER, clean)
        process(clean)
    }

    private fun process(text: String) {
        viewModelScope.launch {
            busy = true
            val thinking = add(Role.STATUS, "Thinking…")
            try {
                if (!models.isReady(ModelSpec.NEEDLE)) {
                    replace(thinking, Role.ASSISTANT, "Download Needle 3 in Settings first.")
                    return@launch
                }
                when (val reply = app.harness.handle(text)) {
                    is Reply.Done -> replace(thinking, Role.ASSISTANT, reply.text)
                    is Reply.NeedsConfirm -> replace(thinking, Role.ASSISTANT, reply.prompt, reply.call)
                }
            } catch (e: Exception) {
                replace(thinking, Role.ASSISTANT, "Something went wrong: ${e.message ?: e}")
            } finally {
                busy = false
            }
        }
    }

    fun answer(message: ChatMessage, accept: Boolean) {
        val call = message.pending ?: return
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0) messages[index] = message.copy(pending = null)
        if (!accept) {
            add(Role.ASSISTANT, "Okay, I won't.")
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                add(Role.ASSISTANT, app.harness.runConfirmed(call))
            } catch (e: Exception) {
                add(Role.ASSISTANT, "Something went wrong: ${e.message ?: e}")
            } finally {
                busy = false
            }
        }
    }

    fun startRecording() {
        if (busy || recording) return
        viewModelScope.launch {
            if (!models.isReady(ModelSpec.WHISTLE)) {
                add(Role.ASSISTANT, "Download Whistle in Settings to use the mic.")
                return@launch
            }
            if (!app.permissions.request(Manifest.permission.RECORD_AUDIO)) {
                add(Role.ASSISTANT, "Nimble needs microphone access for voice.")
                return@launch
            }
            if (!recorder.start(viewModelScope)) {
                add(Role.ASSISTANT, "The microphone is not available.")
                return@launch
            }
            recording = true
            recordSeconds = 0
            while (recording) {
                recordSeconds = recorder.seconds
                delay(250)
            }
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        viewModelScope.launch {
            val samples = recorder.stop()
            if (samples.size < VoiceRecorder.SAMPLE_RATE / 3) {
                add(Role.STATUS, "Too short. Tap the mic, speak, then tap stop.")
                return@launch
            }
            busy = true
            val status = add(Role.STATUS, "Transcribing…")
            try {
                val language = settings.voiceLanguage.takeIf { it != AppSettings.AUTO }
                val text = app.engine.transcribe(samples, language).optString("text").trim()
                remove(status)
                busy = false
                if (text.isEmpty()) add(Role.ASSISTANT, "I didn't catch that.")
                else send(text)
            } catch (e: Exception) {
                replace(status, Role.ASSISTANT, "Voice failed: ${e.message ?: e}")
                busy = false
            }
        }
    }

    fun cancelRecording() {
        recording = false
        recorder.cancel()
    }

    fun download(spec: ModelSpec) = models.download(spec)

    fun deleteModel(spec: ModelSpec) {
        models.delete(spec)
        viewModelScope.launch { app.engine.invalidate(spec) }
    }

    override fun onCleared() {
        recorder.cancel()
    }

    private fun add(role: Role, text: String, pending: PendingCall? = null): Long {
        val id = nextId++
        messages += ChatMessage(id, role, text, pending)
        return id
    }

    private fun replace(id: Long, role: Role, text: String, pending: PendingCall? = null) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = ChatMessage(id, role, text, pending) else add(role, text, pending)
    }

    private fun remove(id: Long) {
        messages.removeAll { it.id == id }
    }
}
