package com.home.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launcher appearance, voice and home screen preferences. */
@Composable
internal fun AppearancePage() {
    Toggle(tr(R.string.voice_assistant_enabled), LauncherSettings.voiceAssistantEnabled, Modifier.fillMaxWidth()) {
        LauncherSettings.voiceAssistantEnabled = !LauncherSettings.voiceAssistantEnabled
    }
    T(tr(R.string.voice_assistant_enabled_hint), 14.sp, color = PanelDim, maxLines = 3)
    val dark = LauncherSettings.dark
    Section(tr(R.string.theme))
    PairRow {
        Chip(tr(R.string.theme_light), !dark, Modifier.weight(1f)) { LauncherSettings.dark = false }
        Chip(tr(R.string.theme_dark), dark, Modifier.weight(1f)) { LauncherSettings.dark = true }
    }
    Section(tr(R.string.background_name, Backgrounds[LauncherSettings.background].name))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Backgrounds.forEachIndexed { i, preset ->
            Swatch(Colors.presetBrush(preset), LauncherSettings.background == i) { LauncherSettings.background = i }
        }
    }
    if (Colors.isXmb) XmbOptions()
    // Each language is named in itself, so it can be found whatever the interface is set to.
    Section(tr(R.string.language))
    PairRow {
        Chip(tr(R.string.language_system), AppLanguage.current == AppLanguage.SYSTEM, Modifier.weight(1f)) { AppLanguage.current = AppLanguage.SYSTEM }
        Chip("Русский", AppLanguage.current == AppLanguage.RU, Modifier.weight(1f)) { AppLanguage.current = AppLanguage.RU }
    }
    PairRow {
        Chip("English", AppLanguage.current == AppLanguage.EN, Modifier.weight(1f)) { AppLanguage.current = AppLanguage.EN }
    }
    HomeScreenSettings()
}

@Composable
private fun HomeScreenSettings() {
    Section(tr(R.string.home_layout))
    PairRow {
        Chip(tr(R.string.layout_switch), LauncherSettings.homeLayout == HOME_SWITCH, Modifier.weight(1f)) {
            LauncherSettings.homeLayout = HOME_SWITCH
        }
        Chip(tr(R.string.layout_google_tv), LauncherSettings.homeLayout == HOME_GOOGLE_TV, Modifier.weight(1f)) {
            LauncherSettings.homeLayout = HOME_GOOGLE_TV
        }
    }
    Section(tr(R.string.show_on_home))
    Toggle(tr(R.string.now_playing), LauncherSettings.nowPlaying, Modifier.fillMaxWidth()) {
        LauncherSettings.nowPlaying = !LauncherSettings.nowPlaying
    }
    Spacer(Modifier.height(10.dp))
    Toggle(tr(R.string.usb_tile), LauncherSettings.usbTile, Modifier.fillMaxWidth()) {
        LauncherSettings.usbTile = !LauncherSettings.usbTile
    }

}

/** XMB colour (by month like the PS3, or fixed) and the animation switch. */
@Composable
private fun XmbOptions() {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        Chip(tr(R.string.color_by_month), LauncherSettings.xmbColor < 0, Modifier.weight(1f)) { LauncherSettings.xmbColor = -1 }
    }
    // Two rows of six: January-June, July-December.
    XmbColors.chunked(6).forEachIndexed { row, colors ->
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            colors.forEachIndexed { col, color ->
                val index = row * 6 + col
                ColorDot(color, LauncherSettings.xmbColor == index) { LauncherSettings.xmbColor = index }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        Toggle(tr(R.string.background_animation), LauncherSettings.bgAnimation, Modifier.weight(1f)) {
            LauncherSettings.bgAnimation = !LauncherSettings.bgAnimation
        }
    }
}
