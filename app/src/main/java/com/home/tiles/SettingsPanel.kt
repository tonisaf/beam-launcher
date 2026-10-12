package com.home.tiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Settings belonging to the launcher, without projector controls or a floating quick panel. */
@Composable
fun SettingsPanel(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Column(Modifier.width(560.dp).fillMaxHeight(0.9f).background(androidx.compose.ui.graphics.Color(0xFF202328))
            .verticalScroll(rememberScrollState()).padding(24.dp).arrowSoundTracker()) {
            Box(Modifier.focusRequester(first)) { ListRow(tr(R.string.setup_close), onDismiss) }
            T(tr(R.string.appearance_and_home), 24.sp, color = PanelText)
            AppearancePage()
            Section(tr(R.string.interface_section))
            Toggle(tr(R.string.nav_sounds), LauncherSettings.sounds, Modifier.fillMaxWidth()) {
                LauncherSettings.sounds = !LauncherSettings.sounds
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos {}
            first.requestFocus()
        }
    }
}
