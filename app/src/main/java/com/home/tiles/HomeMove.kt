package com.home.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keep the draft on Home; usage refreshes do not change the order during a move. */
class HomeMove internal constructor(apps: List<AppEntry>, val pkg: String) {
    var draft by mutableStateOf(apps); private set
    fun move(direction: Int) {
        val next = moveApp(draft, pkg, direction)
        if (next != draft) { draft = next; Sounds.navigate() }
    }
}

@Composable
internal fun MoveHint(session: HomeMove, modifier: Modifier, onSave: () -> Unit, onCancel: () -> Unit) {
    val selected = session.draft.firstOrNull { it.pkg == session.pkg }
    Column(modifier.padding(bottom = 20.dp).width(740.dp)
        .background(Colors.Surface, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 12.dp)) {
        T(tr(R.string.move_app_title, selected?.label.orEmpty()), 19.sp, color = Colors.Accent)
        Spacer(Modifier.height(4.dp))
        T(tr(R.string.move_app_hint), 14.sp, color = Colors.TextDim, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        PairRow {
            Chip(tr(R.string.move_app_left), false, Modifier.weight(1f)) { session.move(-1) }
            Chip(tr(R.string.move_app_right), false, Modifier.weight(1f)) { session.move(1) }
            Chip(tr(R.string.move_app_done), false, Modifier.weight(1f), onClick = onSave)
            Chip(tr(R.string.move_app_cancel), false, Modifier.weight(1f), onClick = onCancel)
        }
    }
}
