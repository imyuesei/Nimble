package app.nimble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nimble.ui.ChatScreen
import app.nimble.ui.ChatViewModel
import app.nimble.ui.NimbleTheme
import app.nimble.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val gate get() = (application as NimbleApp).permissions

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> gate.onResult(granted) }

    private val requester: (String) -> Unit = { permissionLauncher.launch(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        gate.requester = requester
        setContent {
            NimbleTheme {
                val vm: ChatViewModel = viewModel()
                var showSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                if (showSettings) {
                    SettingsScreen(vm, onBack = { showSettings = false })
                } else {
                    ChatScreen(vm, onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    override fun onDestroy() {
        if (gate.requester === requester) gate.requester = null
        super.onDestroy()
    }
}
