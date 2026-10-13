package com.home.tiles

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AppOrderDialog(repo: AppRepository, apps: List<AppEntry>, pkg: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    // Freeze the starting order; package/usage refreshes must not overwrite an in-progress move.
    var draft by remember { mutableStateOf(apps.filter { !it.hidden }) }
    val selected = draft.firstOrNull { it.pkg == pkg }
    val first = remember { FocusRequester() }
    val state = rememberLazyListState()
    val index = draft.indexOfFirst { it.pkg == pkg }
    fun move(direction: Int) {
        val next = moveApp(draft, pkg, direction)
        if (next != draft) { draft = next; Sounds.navigate() }
    }
    fun save() { repo.saveOrder(draft, apps); onSaved(); onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Column(Modifier.width(900.dp).background(Colors.Surface, RoundedCornerShape(28.dp)).padding(28.dp)
            .focusRequester(first)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (native.action == KeyEvent.ACTION_DOWN) move(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (native.action == KeyEvent.ACTION_UP) { Sounds.activate(); save() }
                        true
                    }
                    else -> false
                }
            }.focusable()) {
            T(tr(R.string.move_app_title, selected?.label.orEmpty()), 26.sp)
            Spacer(Modifier.height(12.dp))
            T(tr(R.string.move_app_hint), 16.sp, color = Colors.TextDim, maxLines = 3)
            Spacer(Modifier.height(16.dp))
            T(tr(if (selected?.pinned == true) R.string.app_favourites else R.string.app_other), 16.sp, color = Colors.TextDim)
            LazyRow(state = state, contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(draft, key = { it.pkg }) { app ->
                    val art by produceState(repo.cachedRoundIcon(app), app.pkg, app.updated) { value = repo.loadRoundIcon(app) }
                    Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(90.dp).clip(CircleShape)
                            .then(if (app.pkg == pkg) Modifier.border(3.dp, Colors.Accent, CircleShape) else Modifier)) { AppArt(art, app.label) }
                        if (app.pkg == pkg) T("←  ${index + 1}  →", 18.sp, color = Colors.Accent)
                        else T(if (app.pinned) "★" else "", 18.sp, color = Colors.TextDim)
                        T(app.label, 14.sp, color = if (app.pkg == pkg) Colors.Text else Colors.TextDim)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PairRow {
                Chip(tr(R.string.move_app_left), false, Modifier.weight(1f), enabled = index > 0 && draft.getOrNull(index - 1)?.pinned == selected?.pinned) { move(-1) }
                Chip(tr(R.string.move_app_right), false, Modifier.weight(1f), enabled = index >= 0 && draft.getOrNull(index + 1)?.pinned == selected?.pinned) { move(1) }
                Chip(tr(R.string.move_app_done), true, Modifier.weight(1f)) { save() }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos {}; runCatching { first.requestFocus() } }
        LaunchedEffect(index) { if (index >= 0) state.animateScrollToItem((index - 3).coerceAtLeast(0)) }
    }
}
