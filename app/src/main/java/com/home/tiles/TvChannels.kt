package com.home.tiles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Other apps' home-screen content from the Android TV provider: their preview channels
 * (SmartTube subscriptions, Spotify recents...) and the Watch Next list.
 * Needs READ_TV_LISTINGS (granted over adb) to see channels the stock launcher never approved.
 */
class TvItem(
    val key: String,
    val pkg: String,
    val title: String,
    val subtitle: String,
    val poster: Uri?,
    val intentUri: String?,
)

class TvChannel(val key: String, val name: String, val items: List<TvItem>, val legacyKey: String = key)

const val WATCH_NEXT_KEY = "watchnext"
const val SECOND_ROW_OFF = "off"
const val SECOND_ROW_AUTO = "auto"

private const val MAX_ITEMS = 20

/** Reloads when the TV provider changes and whenever [refreshKey] changes (e.g. coming back home). */
@Composable
fun rememberTvChannels(refreshKey: Any): State<List<TvChannel>> {
    val context = LocalContext.current
    return produceState(emptyList(), refreshKey) {
        suspend fun load() { value = withContext(Dispatchers.IO) { queryTvChannels(context) } }
        load()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { load() }
            }
        }
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, observer)
            resolver.registerContentObserver(TvContract.PreviewPrograms.CONTENT_URI, true, observer)
            resolver.registerContentObserver(TvContract.Channels.CONTENT_URI, true, observer)
        }
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
}

/** The channel chosen for the second row; "auto" prefers SmartTube subscriptions. */
fun pickSecondRow(channels: List<TvChannel>, setting: String): TvChannel? = when (setting) {
    SECOND_ROW_OFF -> null
    SECOND_ROW_AUTO -> channels.firstOrNull { it.legacyKey == "org.smarttube.stable|Подписки" && it.items.isNotEmpty() }
        ?: channels.firstOrNull { it.key == WATCH_NEXT_KEY }
        ?: channels.firstOrNull { it.items.isNotEmpty() }
    else -> channels.firstOrNull { it.key == setting || it.legacyKey == setting }
}?.takeIf { it.items.isNotEmpty() }

fun queryTvChannels(context: Context): List<TvChannel> {
    val resolver = context.contentResolver
    val result = mutableListOf<TvChannel>()

    val watchNext = mutableListOf<Pair<Long, TvItem>>()
    runCatching {
        resolver.query(
            TvContract.WatchNextPrograms.CONTENT_URI,
            arrayOf(
                TvContract.WatchNextPrograms._ID,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                watchNext += c.getLong(6) to TvItem(
                    key = "wn:${c.getLong(0)}",
                    pkg = c.getString(1).orEmpty(),
                    title = c.getString(2).orEmpty(),
                    subtitle = c.getString(3).orEmpty(),
                    poster = c.getString(4)?.let(Uri::parse),
                    intentUri = c.getString(5),
                )
            }
        }
    }
    val watchNextItems = watchNext.sortedByDescending { it.first }.map { it.second }.clean()
    if (watchNextItems.isNotEmpty()) result += TvChannel(WATCH_NEXT_KEY, tr(R.string.continue_watching), watchNextItems)

    val channels = mutableListOf<Triple<Long, String, String>>() // id, package, name
    // TvProvider rejects selection/sort arguments from non-system callers, so filter here.
    runCatching {
        resolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_TYPE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(3) == TvContract.Channels.TYPE_PREVIEW) {
                    channels += Triple(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
                }
            }
        }
    }.onFailure { Log.w(TAG, "Channels query failed", it) }
    for ((id, pkg, name) in channels) {
        val items = mutableListOf<TvItem>()
        runCatching {
            resolver.query(
                TvContract.buildPreviewProgramsUriForChannel(id),
                arrayOf(
                    TvContract.PreviewPrograms._ID,
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    items += TvItem(
                        key = "pp:${c.getLong(0)}",
                        pkg = pkg,
                        title = c.getString(1).orEmpty(),
                        subtitle = c.getString(2).orEmpty(),
                        poster = c.getString(3)?.let(Uri::parse),
                        intentUri = c.getString(4),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Programs of channel $id failed", it) }
        val app = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)) }
            .getOrNull()?.toString()
        val label = if (app == null || app == name) name else "$name · $app"
        result += TvChannel("preview:$id", label, items.clean(), legacyKey = "$pkg|$name")
    }
    return result
}

private const val TAG = "TvChannels"

private fun List<TvItem>.clean() = filter { it.title.isNotBlank() }.distinctBy { it.intentUri ?: it.key }.take(MAX_ITEMS)

fun Context.openTvItem(item: TvItem) {
    val intent = item.intentUri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
    if (intent != null) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { launchPackage(item.pkg) }
    } else {
        launchPackage(item.pkg)
    }
}

/**
 * XGIMI's XTurbo force-stops background apps, which also cancels SmartTube's periodic channel job.
 * Poking its (exported) update receiver directly reschedules the sync; throttled to every 30 minutes.
 */
fun Context.requestChannelRefresh() {
    val prefs = getSharedPreferences("tiles", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    if (now - prefs.getLong(KEY_LAST_REFRESH, 0) < REFRESH_INTERVAL_MS) return
    prefs.edit().putLong(KEY_LAST_REFRESH, now).apply()
    runCatching {
        sendBroadcast(
            Intent("com.home.tiles.REFRESH_CHANNELS").setComponent(
                ComponentName("org.smarttube.stable", "com.liskovsoft.leanbackassistant.channels.UpdateChannelsReceiver"),
            ),
        )
    }
}

private const val KEY_LAST_REFRESH = "channelsRefreshedAt"
private const val REFRESH_INTERVAL_MS = 30L * 60 * 1000

// ---- Posters ----

private val posterCache = BoundedCache<Uri, ImageBitmap>(16 * 1024 * 1024) { it.asAndroidBitmap().allocationByteCount }

private suspend fun loadPoster(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    posterCache[uri]?.let { return@withContext it }
    try {
        runInterruptible {
            // Download once, then inspect and decode the same bytes from disk.
            val file = File.createTempFile("poster-", ".img", context.cacheDir)
            var connection: HttpURLConnection? = null
            try {
                val input = when (uri.scheme) {
                    "http", "https" -> (URL(uri.toString()).openConnection() as HttpURLConnection).also {
                        connection = it
                        it.connectTimeout = 8_000
                        it.readTimeout = 8_000
                    }.inputStream
                    else -> context.contentResolver.openInputStream(uri)
                } ?: return@runInterruptible null
                input.use { stream ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        val deadline = System.nanoTime() + 30_000_000_000L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            check(System.nanoTime() < deadline) { "Poster download timed out" }
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= 20 * 1024 * 1024) { "Poster is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runInterruptible null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 960) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            } finally {
                connection?.disconnect()
                file.delete()
            }
        }?.also { posterCache[uri] = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Poster load failed: $uri", e)
        null
    }
}

// ---- UI ----

@Composable
fun ChannelRow(channel: TvChannel, startPad: Dp, cardWidth: Dp = 208.dp, glow: Boolean = false) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        T(channel.name, 20.sp, Modifier.padding(start = startPad), color = Colors.TextDim)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            // A glowing card needs room around it, or the row clips the glow.
            contentPadding = PaddingValues(start = startPad, end = 60.dp, top = if (glow) 26.dp else 8.dp, bottom = if (glow) 26.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(channel.items, key = { it.key }) { item ->
                TvCard(item, cardWidth, glow) { context.openTvItem(item) }
            }
        }
    }
}

/** Poster URI to the colour its card glows. */
private val glowCache = BoundedCache<Uri, Color>(128) { 1 }

/** The poster's most vivid colour, or its dominant one; null if it can't be read. */
private fun posterColor(image: ImageBitmap): Color? = runCatching {
    val palette = Palette.from(image.asAndroidBitmap()).maximumColorCount(8).resizeBitmapArea(96 * 96).generate()
    val rgb = palette.getVibrantColor(palette.getDominantColor(0))
    if (rgb == 0) null else Color(rgb)
}.getOrNull()

@Composable
private fun TvCard(item: TvItem, width: Dp, glow: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(120), label = "card")
    val poster by produceState<ImageBitmap?>(item.poster?.let { posterCache[it] }, item.poster) {
        item.poster?.let { value = loadPoster(context, it) }
    }
    // The glow's colour: the poster's, found as soon as the poster is in (and only for rows that glow),
    // so it is ready by the time the card is focused.
    val glowColor by produceState<Color?>(item.poster?.let { glowCache[it] }, poster) {
        val uri = item.poster
        val image = poster
        if (!glow || uri == null || image == null) return@produceState
        value = glowCache[uri] ?: withContext(Dispatchers.Default) { posterColor(image) }?.also { glowCache[uri] = it }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(width, width * 9 / 16)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (glow) Modifier.focusGlow(focused, FocusGlowReach, glowTone(glowColor ?: Colors.Accent), circle = false) else Modifier)
            .shadow(if (focused) 6.dp else 3.dp, shape)
            .clip(shape)
            .background(Color(0xFF3A3A3A))
            .then(if (focused) Modifier.pulseBorder(4.dp, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) Sounds.navigate()
            }
            .clickable(remember { MutableInteractionSource() }, null) {
                Sounds.activate()
                onClick()
            },
    ) {
        poster?.let { Image(it, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        // Title over a dark fade so it reads on any artwork.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column {
                T(item.title, 15.sp, color = Color.White)
                if (item.subtitle.isNotEmpty()) T(item.subtitle, 12.sp, color = Color(0xFFCCCCCC))
            }
        }
    }
}
