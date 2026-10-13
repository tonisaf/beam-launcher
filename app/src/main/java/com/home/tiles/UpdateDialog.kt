package com.home.tiles

import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
internal fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var showNotes by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(Updates.autoEnabled(context)) }
    fun dismiss() { Updates.snooze(context); onDismiss() }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(760.dp).heightIn(max = 620.dp)
            .background(Colors.Surface, RoundedCornerShape(32.dp)).padding(32.dp)
            .verticalScroll(rememberScrollState()).arrowSoundTracker()) {
            T(tr(R.string.update_title), 26.sp, color = Colors.Text)
            Spacer(Modifier.height(12.dp))
            T(tr(R.string.update_installed, Updates.version(context)), 16.sp, color = Colors.TextDim)
            Spacer(Modifier.height(12.dp))
            val release = Updates.release
            val status = when {
                Updates.busy && Updates.progress >= 0 -> tr(R.string.update_downloading, Updates.progress)
                Updates.busy -> tr(R.string.update_checking)
                Updates.error -> tr(if (Updates.installError) R.string.update_install_error else R.string.update_error)
                Updates.ready != null -> tr(R.string.update_ready)
                release != null -> tr(R.string.update_available, release.tag)
                Updates.checked -> tr(R.string.update_current)
                else -> tr(R.string.update_hint)
            }
            T(status, 17.sp, color = Colors.Text, maxLines = 3)
            Spacer(Modifier.height(20.dp))
            Chip(tr(R.string.update_check), false, Modifier.fillMaxWidth().focusRequester(first), enabled = !Updates.busy) {
                scope.launch { Updates.check(context) }
            }
            Spacer(Modifier.height(10.dp))
            if (release != null) {
                Chip(tr(if (Updates.ready != null) R.string.update_install else R.string.update_download), false,
                    Modifier.fillMaxWidth(), enabled = !Updates.busy) {
                    if (Updates.ready != null) Updates.install(context) else scope.launch { Updates.download(context) }
                }
                Spacer(Modifier.height(10.dp))
                T(tr(R.string.update_install_hint), 14.sp, color = Colors.TextDim, maxLines = 3)
                if (Updates.ready != null && !context.packageManager.canRequestPackageInstalls()) {
                    Spacer(Modifier.height(8.dp))
                    T(tr(R.string.update_permission_hint), 13.sp, color = Colors.TextDim, maxLines = 4)
                }
                Spacer(Modifier.height(12.dp))
                if (release.notes.isNotBlank()) {
                    Chip(tr(R.string.update_notes), false, Modifier.fillMaxWidth()) { showNotes = true }
                }
            }
            Spacer(Modifier.height(16.dp))
            Toggle(tr(R.string.update_auto), auto, Modifier.fillMaxWidth()) {
                auto = !auto; Updates.setAuto(context, auto)
            }
            Spacer(Modifier.height(12.dp))
            Chip(tr(R.string.update_later), false, Modifier.fillMaxWidth(), onClick = ::dismiss)
        }
        LaunchedEffect(Unit) { withFrameNanos {}; first.requestFocus() }
    }
    if (showNotes) UpdateNotesDialog(Updates.release?.notes.orEmpty()) { showNotes = false }
}

@Composable
private fun UpdateNotesDialog(notes: String, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(760.dp).height(620.dp)
            .background(Colors.Surface, RoundedCornerShape(32.dp)).padding(32.dp)) {
            T(tr(R.string.update_notes), 26.sp, color = Colors.Text)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .focusRequester(first).onPreviewKeyEvent { event ->
                    val canScroll = (event.key == Key.DirectionDown && scroll.value < scroll.maxValue) ||
                        (event.key == Key.DirectionUp && scroll.value > 0)
                    if (event.type == KeyEventType.KeyDown && canScroll) {
                        scope.launch { scroll.scrollTo((scroll.value + if (event.key == Key.DirectionDown) 160 else -160).coerceIn(0, scroll.maxValue)) }
                        true
                    } else false
                }.focusable()) {
                T(notes, 16.sp, color = Colors.Text, maxLines = Int.MAX_VALUE)
            }
            Spacer(Modifier.height(16.dp))
            Chip(tr(R.string.update_notes_close), false, Modifier.fillMaxWidth(), onClick = onDismiss)
        }
        LaunchedEffect(Unit) { withFrameNanos {}; first.requestFocus() }
    }
}
