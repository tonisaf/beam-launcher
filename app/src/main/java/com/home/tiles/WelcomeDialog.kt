package com.home.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Choices apply live, just like the appearance panel; Back keeps the selected settings. */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    fun finish() { LauncherSettings.welcomeDone = true; onDismiss() }
    Dialog(onDismissRequest = ::finish, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(820.dp).heightIn(max = 640.dp)
            .background(Colors.Surface, RoundedCornerShape(32.dp)).padding(32.dp)
            .verticalScroll(rememberScrollState()).arrowSoundTracker()) {
            T(tr(R.string.welcome_title), 28.sp, color = Colors.Text)
            Spacer(Modifier.height(8.dp))
            T(tr(R.string.welcome_hint), 15.sp, color = Colors.TextDim)
            Section(tr(R.string.language))
            PairRow {
                listOf(AppLanguage.SYSTEM to tr(R.string.language_system), AppLanguage.RU to "Русский", AppLanguage.EN to "English")
                    .forEachIndexed { index, (value, label) ->
                        Chip(label, AppLanguage.current == value,
                            Modifier.weight(1f).then(if (index == 0) Modifier.focusRequester(first) else Modifier)) {
                            AppLanguage.current = value
                        }
                    }
            }
            Section(tr(R.string.welcome_layout))
            PairRow {
                LayoutPreview(HOME_SWITCH, tr(R.string.layout_switch), Modifier.weight(1f))
                LayoutPreview(HOME_GOOGLE_TV, tr(R.string.layout_google_tv), Modifier.weight(1f))
            }
            Section(tr(R.string.welcome_theme))
            PairRow {
                Chip(tr(R.string.theme_light), !LauncherSettings.dark, Modifier.weight(1f)) { LauncherSettings.dark = false }
                Chip(tr(R.string.theme_dark), LauncherSettings.dark, Modifier.weight(1f)) { LauncherSettings.dark = true }
            }
            Spacer(Modifier.height(16.dp))
            Chip(tr(R.string.welcome_done), false, Modifier.fillMaxWidth(), onClick = ::finish)
        }
        LaunchedEffect(Unit) { withFrameNanos {}; first.requestFocus() }
    }
}

@Composable
private fun LayoutPreview(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Column(Modifier.fillMaxWidth().height(92.dp).background(CardBg, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(5) { index ->
                    Box(Modifier.size(34.dp).background(
                        Colors.Accent.copy(alpha = 0.25f + index * 0.12f),
                        if (value == HOME_SWITCH) RoundedCornerShape(5.dp) else CircleShape))
                }
            }
            if (value == HOME_GOOGLE_TV) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { Box(Modifier.width(58.dp).height(16.dp).background(Colors.Text.copy(alpha = 0.15f), RoundedCornerShape(3.dp))) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Chip(label, LauncherSettings.homeLayout == value, Modifier.fillMaxWidth()) { LauncherSettings.homeLayout = value }
    }
}
