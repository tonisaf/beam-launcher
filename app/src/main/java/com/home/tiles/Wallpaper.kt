package com.home.tiles

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.AtomicFile
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

private fun wallpaperFile(context: Context) = File(context.filesDir, "wallpaper.jpg")

/** Copy only a bounded, decoded image into app storage; never retain a provider permission. */
internal fun importWallpaper(context: Context, uri: Uri, destination: File = wallpaperFile(context)) {
    val temporary = File.createTempFile("wallpaper-", ".image", context.cacheDir)
    var bitmap: Bitmap? = null
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 32L * 1024 * 1024) { "Image exceeds 32 MB" }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Cannot read image")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temporary.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { "Image dimensions too large" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
        }
        bitmap = BitmapFactory.decodeFile(temporary.path, options) ?: error("Cannot decode image")
        // Camera JPEGs often store rotation separately from their pixel data.
        val orientation = runCatching {
            android.media.ExifInterface(temporary.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        }.getOrDefault(1)
        val matrix = android.graphics.Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val target = AtomicFile(destination)
        val output = target.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    } finally {
        bitmap?.recycle()
        temporary.delete()
    }
}

internal object WallpaperPreview {
    data class Draft(val file: File, val bitmap: androidx.compose.ui.graphics.ImageBitmap, val dim: Int)
    var draft by mutableStateOf<Draft?>(null)
    var savedBitmap: Pair<Long, androidx.compose.ui.graphics.ImageBitmap>? = null
    fun cancel() {
        draft?.file?.delete()
        draft = null
    }
}

@Composable
internal fun WallpaperBackground() {
    val context = LocalContext.current
    val revision = LauncherSettings.wallpaper
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, revision) {
        value = runInterruptible(Dispatchers.IO) {
            runCatching { AtomicFile(wallpaperFile(context)).openRead().use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val draft = WallpaperPreview.draft
    val active = draft?.bitmap ?: WallpaperPreview.savedBitmap?.takeIf { it.first == revision }?.second ?: bitmap
    active?.let {
        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (draft?.dim ?: LauncherSettings.wallpaperDim) / 100f)))
    }
}

@Composable
internal fun WallpaperSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf(false) }
    fun preview(load: (File) -> Unit) {
        if (busy) return
        gallery = false
        busy = true
        scope.launch {
            var previewFile: File? = null
            try {
                val prepared = runInterruptible(Dispatchers.IO) {
                    val file = File.createTempFile("wallpaper-preview-", ".jpg", context.cacheDir)
                    previewFile = file
                    load(file)
                    val bitmap = BitmapFactory.decodeFile(file.path)?.asImageBitmap() ?: error("Cannot decode preview")
                    WallpaperPreview.Draft(file, bitmap, LauncherSettings.wallpaperDim)
                }
                WallpaperPreview.draft = prepared
                previewFile = null
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, tr(R.string.wallpaper_failed), Toast.LENGTH_LONG).show() }
            finally { previewFile?.delete(); busy = false }
        }
    }
    fun import(uri: Uri) = preview { file -> importWallpaper(context, uri, file) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.let { import(it) }
    }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) gallery = true
        else Toast.makeText(context, tr(R.string.wallpaper_permission), Toast.LENGTH_LONG).show()
    }
    Spacer(Modifier.height(12.dp))
    ListRow(tr(if (busy) R.string.wallpaper_loading else R.string.wallpaper_choose)) {
        if (!busy) {
            val intent = listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT).map { action ->
                Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            }.firstOrNull { it.resolveActivity(context.packageManager) != null }
            if (intent != null) {
                try { picker.launch(intent) }
                catch (_: Exception) { Toast.makeText(context, tr(R.string.wallpaper_failed), Toast.LENGTH_LONG).show() }
            } else if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) gallery = true
            else request.launch(permission)
        }
    }
    if (LauncherSettings.wallpaper != 0L) {
        ListRow(tr(R.string.wallpaper_preview)) {
            preview { file -> AtomicFile(wallpaperFile(context)).openRead().use { input -> file.outputStream().use { input.copyTo(it) } } }
        }
        ListRow(tr(R.string.wallpaper_reset)) { if (!busy) LauncherSettings.wallpaper = 0L }
    }
    if (LauncherSettings.wallpaper != 0L) {
        Section(tr(R.string.wallpaper_dim))
        LevelSlider(Icons.Rounded.Brightness6, LauncherSettings.wallpaperDim, 80, Modifier.fillMaxWidth()) {
            LauncherSettings.wallpaperDim = it
        }
    }
    if (gallery) WallpaperGallery(onDismiss = { gallery = false }, onChoose = ::import)
}

private data class GalleryImage(val uri: Uri, val name: String)

@Composable
private fun WallpaperGallery(onDismiss: () -> Unit, onChoose: (Uri) -> Unit) {
    val context = LocalContext.current
    val images by produceState<List<GalleryImage>?>(null) {
        value = runInterruptible(Dispatchers.IO) {
            runCatching {
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME), null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                    buildList {
                        while (size < 300 && cursor.moveToNext()) {
                            add(GalleryImage(ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(1) ?: "Image"))
                        }
                    }
                } ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(900.dp).height(560.dp).clip(RoundedCornerShape(24.dp)).background(Colors.Surface).padding(24.dp).arrowSoundTracker()) {
            ListRow(tr(R.string.setup_close)) { onDismiss() }
            Section(tr(R.string.wallpaper_choose))
            when {
                images == null -> T(tr(R.string.wallpaper_loading), 18.sp)
                images!!.isEmpty() -> T(tr(R.string.wallpaper_empty), 18.sp, maxLines = 4)
                else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(images!!, key = { it.uri.toString() }) { image -> GalleryRow(image) { onChoose(image.uri) } }
                }
            }
        }
    }
}

@Composable
private fun GalleryRow(image: GalleryImage, onChoose: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.uri) {
        value = runInterruptible(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(image.uri, android.util.Size(400, 225), null).asImageBitmap()
                else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(image.uri), MediaStore.Images.Thumbnails.MINI_KIND, null)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Column(Modifier.fillMaxWidth().height(158.dp)
        .background(if (focused) FocusBg else CardBg, RoundedCornerShape(16.dp))
        .panelControl({ focused = it }, onChoose).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.15f))) {
            thumbnail?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        T(image.name, 15.sp, Modifier.padding(top = 8.dp), color = if (focused) FocusText else PanelText)
    }
}

/** Modal controls leave Home visible behind them, without applying the draft until confirmation. */
@Composable
internal fun WallpaperPreviewControls() {
    val draft = WallpaperPreview.draft ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    fun cancel() { if (!saving) WallpaperPreview.cancel() }
    Dialog(onDismissRequest = ::cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        BackHandler(onBack = ::cancel)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.width(760.dp).clip(RoundedCornerShape(24.dp)).background(Colors.Surface).padding(18.dp).arrowSoundTracker()) {
                T(tr(R.string.wallpaper_preview), 22.sp)
                Spacer(Modifier.height(10.dp))
                LevelSlider(Icons.Rounded.Brightness6, draft.dim, 80, Modifier.fillMaxWidth().focusRequester(first), label = tr(R.string.wallpaper_dim)) {
                    if (!saving) WallpaperPreview.draft = draft.copy(dim = it)
                }
                Spacer(Modifier.height(10.dp))
                PairRow {
                    Chip(tr(if (saving) R.string.wallpaper_loading else R.string.wallpaper_apply), false, Modifier.weight(1f)) {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                try {
                                    runInterruptible(Dispatchers.IO) {
                                        val target = AtomicFile(wallpaperFile(context))
                                        val output = target.startWrite()
                                        try {
                                            draft.file.inputStream().use { it.copyTo(output) }
                                            target.finishWrite(output)
                                        } catch (error: Exception) { target.failWrite(output); throw error }
                                    }
                                    val revision = maxOf(System.currentTimeMillis(), LauncherSettings.wallpaper + 1)
                                    WallpaperPreview.savedBitmap = revision to draft.bitmap
                                    LauncherSettings.wallpaperDim = draft.dim
                                    LauncherSettings.wallpaper = revision
                                    WallpaperPreview.cancel()
                                } catch (error: CancellationException) { throw error }
                                catch (_: Exception) { Toast.makeText(context, tr(R.string.wallpaper_failed), Toast.LENGTH_LONG).show() }
                                finally { saving = false }
                            }
                        }
                    }
                    Chip(tr(R.string.wallpaper_cancel), false, Modifier.weight(1f)) { cancel() }
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos {}; first.requestFocus() }
    }
}
