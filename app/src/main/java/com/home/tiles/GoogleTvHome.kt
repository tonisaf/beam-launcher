package com.home.tiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class GoogleTvTab { Home, Apps }

private val GtvPad = 56.dp

/** The apps row's icons, in dp: Google TV's 152 px on a 1080p screen, which here is 1280dp wide. */
private val GtvIcon = 101.dp

/** The gap between icons: Google TV's 30 px. */
private val GtvIconGap = 20.dp

/** Wide channel cards, like Google TV's content rows (392 px wide). */
private val GtvCard = 262.dp

/** How far a focused item's glow reaches past its edge; the rows leave this much room so it isn't cut off. */
private val GtvGlow = 26.dp

private const val GTV_MAX_CHANNELS = 3

// TV's default pivot scrolls even an already visible icon back to its focus line.
// Its animated scale changes that target and rocks the whole column while moving
// left or right. Vertically, only scroll enough to reveal an offscreen item.
@OptIn(ExperimentalFoundationApi::class)
internal val HomeVerticalBringIntoViewSpec = object : BringIntoViewSpec {}

/**
 * The channels the Google TV layout lists as rows: all that have content (a few) on automatic, the
 * chosen one, or none when the second row is off.
 */
internal fun googleTvChannels(channels: List<TvChannel>, setting: String): List<TvChannel> = when (setting) {
    SECOND_ROW_OFF -> emptyList()
    SECOND_ROW_AUTO -> channels.filter { it.items.isNotEmpty() }.take(GTV_MAX_CHANNELS)
    else -> listOfNotNull(pickSecondRow(channels, setting))
}

/**
 * A home screen laid out like Google TV: a capsule of tabs on top (Home, Apps), then rows of
 * channel cards and a row of round app icons. The same tiles, ordering and options as the Switch
 * layout, only arranged differently.
 */
@Composable
internal fun GoogleTvHome(
    repo: AppRepository,
    items: List<RowItem>,
    apps: List<AppEntry>,
    channels: List<TvChannel>,
    resumeTick: Int,
    onOpenPanel: () -> Unit,
    onSetup: (() -> Unit)?,
    clickFor: (RowItem) -> () -> Unit,
    longClickFor: (RowItem) -> () -> Unit,
    onOptions: (AppEntry) -> Unit,
) {
    var tab by remember { mutableStateOf(GoogleTvTab.Home) }
    val first = remember { FocusRequester() }
    // Bumped by Back from the Apps tab, so the focus that tab held isn't lost.
    var backTick by remember { mutableIntStateOf(0) }

    // Coming home shows the Home tab with focus on the first app, like the Switch layout.
    LaunchedEffect(resumeTick, backTick) {
        tab = GoogleTvTab.Home
        repeat(10) {
            withFrameNanos {}
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // Back from the Apps tab returns to Home instead of doing nothing.
    BackHandler(enabled = tab == GoogleTvTab.Apps) { backTick++ }

    Column(Modifier.fillMaxSize()) {
        GtvTopBar(tab, onTab = { tab = it }, onOpenPanel, onSetup)
        when (tab) {
            GoogleTvTab.Home -> HomeTab(repo, items, channels, first, clickFor, longClickFor)
            GoogleTvTab.Apps -> AppsTab(repo, apps, onOptions)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeTab(
    repo: AppRepository,
    items: List<RowItem>,
    channels: List<TvChannel>,
    first: FocusRequester,
    clickFor: (RowItem) -> () -> Unit,
    longClickFor: (RowItem) -> () -> Unit,
) {
    val horizontalBringIntoViewSpec = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides HomeVerticalBringIntoViewSpec) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The first channel on top like Google TV's hero row, then the apps, then the rest.
            channels.take(1).forEach { channel ->
                item(key = "channel:${channel.key}") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoViewSpec) {
                        ChannelRow(channel, GtvPad, GtvCard, glow = true)
                    }
                }
            }
            item(key = "apps") {
                Column(Modifier.fillMaxWidth()) {
                    T(tr(R.string.gtv_your_apps), 20.sp, Modifier.padding(start = GtvPad), color = Colors.TextDim)
                    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoViewSpec) {
                        LazyRow(
                            contentPadding = PaddingValues(start = GtvPad, end = 60.dp, top = GtvGlow, bottom = GtvGlow / 2),
                            horizontalArrangement = Arrangement.spacedBy(GtvIconGap),
                        ) {
                            itemsIndexed(items, key = { _, item -> item.key }) { i, item ->
                                RoundTile(
                                    repo, item, GtvIcon,
                                    requester = if (i == 0) first else null,
                                    onClick = clickFor(item),
                                    onLongClick = longClickFor(item),
                                )
                            }
                        }
                    }
                }
            }
            channels.drop(1).forEach { channel ->
                item(key = "channel:${channel.key}") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoViewSpec) {
                        ChannelRow(channel, GtvPad, GtvCard, glow = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsTab(repo: AppRepository, apps: List<AppEntry>, onOptions: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GtvIcon + GtvIconGap),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GtvPad - GtvIconGap / 2, end = 60.dp, top = GtvGlow, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sorted, key = { it.pkg }) { entry ->
            // A cell is one icon plus a gap; the icon sits in the middle of it.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                RoundTile(
                    repo, RowItem.App(entry), GtvIcon,
                    requester = null,
                    onClick = { context.launchApp(entry) },
                    onLongClick = { onOptions(entry) },
                    alwaysShowName = true,
                    dimmed = entry.hidden,
                )
            }
        }
    }
}

/** A round app icon; the focused one grows, gets a ring and shows its name below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundTile(
    repo: AppRepository,
    item: RowItem,
    size: Dp,
    requester: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    alwaysShowName: Boolean = false,
    dimmed: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val press = remember { DpadPress() }
    val scale by animateFloatAsState(if (pressed) 0.95f else if (focused) 1.1f else 1f, tween(100), label = "round")
    // An app shows its own launcher icon, and its glow is that icon's colour; HDMI and USB keep their art.
    val app = (item as? RowItem.App)?.entry
    val icon by produceState<TileArt?>(app?.let { repo.cachedRoundIcon(it) }, app?.pkg, app?.updated) {
        value = app?.let { repo.loadRoundIcon(it) }
    }
    val click = {
        Sounds.activate()
        onClick()
    }
    Column(Modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (dimmed) 0.45f else 1f
                }
                .focusGlow(focused, GtvGlow, glowTone(icon?.top ?: Colors.Accent))
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) Sounds.navigate() else pressed = false
                }
                .dpadClick(press, onPress = { pressed = it }, onClick = click, onLongClick = onLongClick)
                .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLongClick, onClick = click),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(if (focused) 12.dp else 2.dp, CircleShape)
                    .clip(CircleShape),
            ) { if (app != null) AppArt(icon, app.label) else RowItemArt(repo, item) }
            if (focused) Box(Modifier.fillMaxSize().border(3.dp, Colors.Text, CircleShape))
        }
        Spacer(Modifier.height(10.dp))
        // The name's room is kept, so rows don't jump as focus moves. It is as wide as an icon plus
        // its gap (the grid's pitch), so neighbours' names never overlap; a longer one is cut short.
        Box(Modifier.height(24.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (focused || alwaysShowName) {
                T(
                    item.title, 16.sp,
                    Modifier.requiredWidth(size + GtvIconGap),
                    color = if (focused) Colors.Text else Colors.TextDim,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

// ---- Top bar ----

@Composable
private fun GtvTopBar(tab: GoogleTvTab, onTab: (GoogleTvTab) -> Unit, onOpenPanel: () -> Unit, onSetup: (() -> Unit)?) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = GtvPad, end = 60.dp, top = 26.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Capsule {
            TabChip(tr(R.string.gtv_tab_home), tab == GoogleTvTab.Home) { onTab(GoogleTvTab.Home) }
            TabChip(tr(R.string.gtv_tab_apps), tab == GoogleTvTab.Apps) { onTab(GoogleTvTab.Apps) }
        }
        Spacer(Modifier.width(16.dp))
        Capsule {
            IconChip(Icons.Rounded.Tune) { context.openQuickPanel() }
            IconChip(Icons.Rounded.Settings) { context.openSettings() }
            IconChip(Icons.Rounded.Palette, onClick = onOpenPanel)
            // Only while something Beam needs from adb is missing.
            onSetup?.let { IconChip(Icons.Rounded.Warning, tint = Color(0xFFF5A623), onClick = it) }
        }
        Spacer(Modifier.weight(1f))
        val nowPlaying by rememberNowPlaying()
        nowPlaying?.takeIf { LauncherSettings.nowPlaying }?.let {
            NowPlayingBar(it)
            Spacer(Modifier.width(32.dp))
        }
        StatusCluster()
    }
}

/** The rounded track tabs and icons sit on. */
@Composable
private fun Capsule(content: @Composable () -> Unit) {
    val track = if (LauncherSettings.dark) Color(0x33FFFFFF) else Color(0x1A000000)
    Row(
        Modifier.background(track, RoundedCornerShape(50)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Selecting follows focus, like Google TV's tabs: moving onto one shows its page. */
@Composable
private fun TabChip(text: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val filled = selected || focused
    Box(
        Modifier
            .height(48.dp)
            .background(if (filled) Colors.Text else Color.Transparent, RoundedCornerShape(50))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    Sounds.navigate()
                    onSelect()
                }
            }
            .clickable(remember { MutableInteractionSource() }, null) {
                Sounds.activate()
                onSelect()
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 20.sp, color = if (filled) Colors.Background else Colors.Text)
    }
}

@Composable
private fun IconChip(icon: ImageVector, tint: Color? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(48.dp)
            .background(if (focused) Colors.Text else Color.Transparent, CircleShape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) Sounds.navigate()
            }
            .clickable(remember { MutableInteractionSource() }, null) {
                Sounds.activate()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            icon, null, Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(if (focused) Colors.Background else tint ?: Colors.Text),
        )
    }
}


private const val GLOW_LAYERS = 10

/**
 * A glow colour that shows against the dark background: the item's own hue, but vivid and bright,
 * so a navy poster or a near-black icon still glows blue instead of a dull grey.
 */
internal fun glowTone(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
    hsv[2] = maxOf(hsv[2], 0.8f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * A soft glow around the focused item, fading out [reach] past its edge. Drawn once when focus
 * arrives (no animation, so nothing redraws every frame the way the pulsing frame would), and only
 * for the one focused item. The row it sits in must leave [reach] of room around it.
 */
internal fun Modifier.focusGlow(focused: Boolean, reach: Dp, tint: Color, circle: Boolean = true): Modifier =
    if (!focused) this else composed {
        val color = tint.copy(alpha = 0.55f)
        drawBehind {
            val extra = reach.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val inner = if (circle) size.minDimension / 2 else maxOf(size.width, size.height) / 2
            val outer = inner + extra
            val brush = Brush.radialGradient(
                0f to color,
                (inner / outer).coerceIn(0f, 0.99f) to color,
                1f to Color.Transparent,
                center = center,
                radius = outer,
            )
            if (circle) {
                drawCircle(brush, outer, center)
            } else {
                // A wide card: a radial gradient would fade along its length but not across it, so
                // the glow is a stack of rounded rectangles, each a little larger and fainter.
                for (i in GLOW_LAYERS downTo 1) {
                    val grow = extra * i / GLOW_LAYERS
                    drawRoundRect(
                        color.copy(alpha = color.alpha / GLOW_LAYERS * 2.2f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(grow + 12.dp.toPx()),
                    )
                }
            }
        }
    }
