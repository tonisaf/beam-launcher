package com.home.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ClassicPad = 108.dp
private val ClassicGap = 14.dp

/**
 * The Switch home screen: equal-size tiles in a normally scrolling row (tiles to the left stay in
 * view), the selected one framed with its name above; the channel row, if any, underneath.
 */
@Composable
internal fun ClassicHome(
    repo: AppRepository,
    items: List<RowItem>,
    resumeTick: Int,
    channels: List<ChannelPlacement>,
    modifier: Modifier,
    clickFor: (RowItem) -> () -> Unit,
    longClickFor: (RowItem) -> () -> Unit,
) {
    val listState = rememberLazyListState()
    val verticalState = rememberLazyListState()
    val above = channels.filter { it.position == ChannelPosition.ABOVE && it.channel.items.isNotEmpty() }
    val below = channels.filter { it.position == ChannelPosition.BELOW && it.channel.items.isNotEmpty() }
    val first = remember { FocusRequester() }
    var focusedKey by remember { mutableStateOf<String?>(null) }

    // Coming home puts focus back on the first tile, like the row layout.
    LaunchedEffect(resumeTick, items.map { it.key }) {
        listState.scrollToItem(0)
        verticalState.scrollToItem(0)
        repeat(10) { attempt ->
            withFrameNanos {}
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            if (attempt == 1) verticalState.scrollToItem(above.size)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // About four and a half tiles across, 256dp at most like the Switch on a 1280dp screen.
        val tile = ((maxWidth - ClassicPad) / 4.5f).coerceAtMost(256.dp)
        LazyColumn(Modifier.fillMaxSize(), state = verticalState,
            verticalArrangement = if (above.isEmpty() && below.isEmpty()) Arrangement.Center else Arrangement.spacedBy(14.dp)) {
            above.forEach { placement ->
                item(key = placement.channel.key) { ChannelRow(placement.channel, ClassicPad) }
            }
            item(key = "apps") {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(start = ClassicPad, end = ClassicPad, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(ClassicGap),
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { i, item ->
                    val focused = focusedKey == item.key
                    Column {
                        // Name above the selected tile; may run wider than the tile.
                        Box(Modifier.width(tile).height(40.dp)) {
                            if (focused) {
                                T(
                                    item.title,
                                    22.sp,
                                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                                    color = Colors.Accent,
                                )
                            }
                        }
                        Tile(
                            size = tile,
                            highlighted = focused,
                            modifier = Modifier
                                .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) focusedKey = item.key
                                    else if (focusedKey == item.key) focusedKey = null
                                },
                            onClick = clickFor(item),
                            onLongClick = longClickFor(item),
                        ) { RowItemArt(repo, item) }
                    }
                }
            }
            }
            below.forEach { placement ->
                item(key = placement.channel.key) { ChannelRow(placement.channel, ClassicPad) }
            }
        }
    }
}
