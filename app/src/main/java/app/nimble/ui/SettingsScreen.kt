package app.nimble.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nimble.BuildConfig
import app.nimble.R
import app.nimble.models.ModelSpec
import app.nimble.models.ModelState
import app.nimble.settings.AppSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val states by vm.models.states.collectAsStateWithLifecycle()
    val settings = vm.settings

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                title = "Settings",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                    }
                },
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                Section("Models")
                ModelSpec.entries.forEach { spec ->
                    ModelRow(
                        spec = spec,
                        state = states[spec] ?: ModelState.Missing,
                        onDownload = { vm.download(spec) },
                        onCancel = { vm.models.cancel(spec) },
                        onDelete = { vm.deleteModel(spec) },
                    )
                }
                Caption("Downloaded once from Hugging Face. After that everything runs offline on this phone.")

                Section("Performance")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CPU threads", style = MaterialTheme.typography.bodyLarge)
                        Caption("This phone has ${settings.cores} cores. More threads are faster up to its big cores; fewer save battery.")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { settings.threads -= 1 },
                        enabled = settings.threads > 1,
                        modifier = Modifier.size(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("−") }
                    Text(
                        "${settings.threads}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    OutlinedButton(
                        onClick = { settings.threads += 1 },
                        enabled = settings.threads < settings.cores,
                        modifier = Modifier.size(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("+") }
                }

                Section("Safety")
                Text(
                    "Ask before acting when confidence is below ${(settings.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = settings.confidence,
                    onValueChange = { settings.confidence = (it * 20).roundToInt() / 20f },
                    valueRange = 0f..1f,
                    steps = 19,
                )
                Caption("Some actions, like deleting all notes, always ask first.")

                Section("Voice")
                LanguagePicker(settings)

                Section("About")
                Caption("Nimble ${BuildConfig.VERSION_NAME} · Cactus engine 4.1.4 · Needle 3 + Whistle")
                Caption("No cloud: requests never leave this phone.")
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.size(20.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.size(6.dp))
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_000_000.0)

@Composable
private fun ModelRow(
    spec: ModelSpec,
    state: ModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spec.title, style = MaterialTheme.typography.bodyLarge)
                val subtitle = when (state) {
                    ModelState.Missing -> spec.purpose
                    is ModelState.Downloading ->
                        if (state.total > 0) "Downloading ${mb(state.bytes)} of ${mb(state.total)}"
                        else "Downloading ${mb(state.bytes)}"
                    is ModelState.Ready -> "${spec.purpose} · ${mb(state.bytes)}"
                    is ModelState.Failed -> "Failed: ${state.message}"
                }
                Caption(subtitle)
            }
            when (state) {
                ModelState.Missing -> TextButton(onClick = onDownload) { Text("Download") }
                is ModelState.Failed -> TextButton(onClick = onDownload) { Text("Retry") }
                is ModelState.Downloading -> TextButton(onClick = onCancel) { Text("Cancel") }
                is ModelState.Ready -> TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
        if (state is ModelState.Downloading) {
            val fraction = state.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LanguagePicker(settings: AppSettings) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Language", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(AppSettings.LANGUAGES[settings.voiceLanguage] ?: settings.voiceLanguage)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                AppSettings.LANGUAGES.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            settings.voiceLanguage = code
                            open = false
                        },
                    )
                }
            }
        }
    }
}
