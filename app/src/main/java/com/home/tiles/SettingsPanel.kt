package com.home.tiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Launcher preferences in the same visual language as the Google TV home screen. */
@Composable
fun SettingsPanel(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.width(520.dp).fillMaxHeight().clip(RoundedCornerShape(32.dp))
                .background(Colors.Surface).padding(28.dp).arrowSoundTracker()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).focusRequester(first)
                        .background(if (closeFocused) Colors.Text else CardBg, CircleShape)
                        .onFocusChanged { closeFocused = it.isFocused; if (it.isFocused) Sounds.navigate() }
                        .clickable(remember { MutableInteractionSource() }, null) { Sounds.activate(); onDismiss() },
                        contentAlignment = Alignment.Center) {
                        Image(Icons.Rounded.ArrowBack, tr(R.string.setup_close), Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(if (closeFocused) Colors.Background else Colors.Text))
                    }
                    Spacer(Modifier.width(16.dp))
                    T(tr(R.string.appearance_and_home), 24.sp, Modifier.weight(1f), color = Colors.Text)
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    AppearancePage()
                    Section(tr(R.string.interface_section))
                    Toggle(tr(R.string.nav_sounds), LauncherSettings.sounds, Modifier.fillMaxWidth()) {
                        LauncherSettings.sounds = !LauncherSettings.sounds
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos {}
            first.requestFocus()
        }
    }
}
