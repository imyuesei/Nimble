package app.nimble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nimble.R
import app.nimble.models.ModelSpec
import app.nimble.models.ModelState

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val states by vm.models.states.collectAsStateWithLifecycle()
    val needleReady = states[ModelSpec.NEEDLE] is ModelState.Ready

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                title = "Nimble",
                action = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.messages.isEmpty()) EmptyState(needleReady, onOpenSettings) else MessageList(vm)
            }
            InputBar(vm)
        }
    }
}

@Composable
private fun EmptyState(needleReady: Boolean, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (needleReady) "Try “set an alarm for 7am”, “text Sam I'm running late” or “turn on the flashlight”."
            else "Nimble runs entirely on your phone. Download the models once to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!needleReady) {
            Spacer(Modifier.size(16.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("Download models") }
        }
    }
}

@Composable
private fun MessageList(vm: ChatViewModel) {
    val listState = rememberLazyListState()
    val newestFirst = vm.messages.reversed()
    LaunchedEffect(vm.messages.size) { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(newestFirst, key = { it.id }) { message -> MessageRow(message, vm::answer) }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, onAnswer: (ChatMessage, Boolean) -> Unit) {
    if (message.role == Role.STATUS) {
        Text(
            message.text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        return
    }
    val mine = message.role == Role.USER
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            color = if (mine) colors.primary else colors.surfaceVariant,
            contentColor = if (mine) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                if (message.pending != null) {
                    Row(Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = { onAnswer(message, false) }) { Text("Cancel") }
                        TextButton(onClick = { onAnswer(message, true) }) { Text("Run") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(vm: ChatViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val showSend = text.isNotBlank() && !vm.recording
    val submit = {
        if (!vm.busy && text.isNotBlank()) {
            vm.send(text)
            text = ""
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceVariant,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            if (vm.recording) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).padding(start = 18.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(Color(0xFFE53935), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "%d:%02d".format(vm.recordSeconds / 60, vm.recordSeconds % 60),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::cancelRecording) { Text("Cancel") }
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text("Message", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                when {
                    vm.recording -> vm.stopRecording()
                    showSend -> submit()
                    else -> vm.startRecording()
                }
            },
            enabled = vm.recording || !vm.busy,
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
        ) {
            val (icon, label) = when {
                vm.recording -> R.drawable.ic_stop to "Stop and send"
                showSend -> R.drawable.ic_send to "Send"
                else -> R.drawable.ic_mic to "Record voice"
            }
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}
