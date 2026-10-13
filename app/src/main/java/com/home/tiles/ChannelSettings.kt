package com.home.tiles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChannelSettings() {
    val channels by rememberTvChannels(Unit)
    val placements = channelPlacements(channels, LauncherSettings.channelRules, LauncherSettings.secondRow)
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusAfterMove by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusAfterMove, LauncherSettings.channelRules) {
        val target = focusAfterMove ?: return@LaunchedEffect
        withFrameNanos {}
        requesters[target]?.let { runCatching { it.requestFocus() } }
        focusAfterMove = null
    }
    T(tr(R.string.channel_rows_hint), 14.sp, color = PanelDim, maxLines = 4)
    if (channels.isEmpty()) {
        Section(tr(R.string.channel_rows_empty))
    }
    fun save(rows: List<ChannelPlacement>, focusKey: String) {
        focusAfterMove = focusKey
        LauncherSettings.channelRules = placementsToRules(rows, LauncherSettings.channelRules)
    }
    for (position in listOf(ChannelPosition.ABOVE, ChannelPosition.BELOW, ChannelPosition.HIDDEN)) {
        Section(tr(position.labelRes()))
        val group = placements.filter { it.position == position }
        group.forEachIndexed { index, placement ->
            key(placement.channel.key) {
                val channel = placement.channel
                val requester = requesters.getOrPut(channel.key) { FocusRequester() }
                Toggle(channel.name, position != ChannelPosition.HIDDEN, Modifier.fillMaxWidth().focusRequester(requester)) {
                    save(placements.map { if (it.channel.key == channel.key) it.copy(position =
                        if (position == ChannelPosition.HIDDEN) ChannelPosition.BELOW else ChannelPosition.HIDDEN) else it }, channel.key)
                }
                T(tr(R.string.channel_card_count, channel.items.size), 12.sp, Modifier.padding(vertical = 6.dp), color = PanelDim)
                if (position != ChannelPosition.HIDDEN) {
                    PairRow {
                        Chip(tr(R.string.channel_above), position == ChannelPosition.ABOVE, Modifier.weight(1f)) {
                            save(placements.map { if (it.channel.key == channel.key) it.copy(position = ChannelPosition.ABOVE) else it }, channel.key)
                        }
                        Chip(tr(R.string.channel_below), position == ChannelPosition.BELOW, Modifier.weight(1f)) {
                            save(placements.map { if (it.channel.key == channel.key) it.copy(position = ChannelPosition.BELOW) else it }, channel.key)
                        }
                    }
                    PairRow {
                        Chip(tr(R.string.channel_move_up), false, Modifier.weight(1f), enabled = index > 0) {
                            save(moveChannel(placements, channel.key, -1), channel.key)
                        }
                        Chip(tr(R.string.channel_move_down), false, Modifier.weight(1f), enabled = index < group.lastIndex) {
                            save(moveChannel(placements, channel.key, 1), channel.key)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    ListRow(tr(R.string.channel_reset)) {
        LauncherSettings.secondRow = SECOND_ROW_AUTO
        LauncherSettings.channelRules = null
    }
}

private fun ChannelPosition.labelRes(): Int = when (this) {
    ChannelPosition.ABOVE -> R.string.channel_above
    ChannelPosition.BELOW -> R.string.channel_below
    ChannelPosition.HIDDEN -> R.string.channel_hidden
}
