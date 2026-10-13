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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
internal fun importWallpaper(context: Context, uri: Uri) {
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
        val target = AtomicFile(wallpaperFile(context))
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

@Composable
internal fun WallpaperBackground() {
    val context = LocalContext.current
    val revision = LauncherSettings.wallpaper
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, revision) {
        value = runInterruptible(Dispatchers.IO) {
            runCatching { AtomicFile(wallpaperFile(context)).openRead().use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
    }
}

@Composable
internal fun WallpaperSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf(false) }
    fun import(uri: Uri) {
        if (busy) return
        gallery = false
        busy = true
        scope.launch {
            try {
                runInterruptible(Dispatchers.IO) { importWallpaper(context, uri) }
                LauncherSettings.wallpaper = maxOf(System.currentTimeMillis(), LauncherSettings.wallpaper + 1)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, tr(R.string.wallpaper_failed), Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
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
    if (LauncherSettings.wallpaper != 0L) ListRow(tr(R.string.wallpaper_reset)) { if (!busy) LauncherSettings.wallpaper = 0L }
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
        Column(Modifier.width(640.dp).height(560.dp).clip(RoundedCornerShape(24.dp)).background(Colors.Surface).padding(24.dp).arrowSoundTracker()) {
            ListRow(tr(R.string.setup_close)) { onDismiss() }
            Section(tr(R.string.wallpaper_choose))
            when {
                images == null -> T(tr(R.string.wallpaper_loading), 18.sp)
                images!!.isEmpty() -> T(tr(R.string.wallpaper_empty), 18.sp, maxLines = 4)
                else -> LazyColumn(Modifier.weight(1f)) {
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
        if (Build.VERSION.SDK_INT >= 29) value = runInterruptible(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(image.uri, android.util.Size(128, 72), null).asImageBitmap() }.getOrNull()
        }
    }
    Row(Modifier.padding(bottom = 8.dp).fillMaxWidth().height(72.dp)
        .background(if (focused) FocusBg else CardBg, RoundedCornerShape(16.dp))
        .panelControl({ focused = it }, onChoose).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        thumbnail?.let { Image(it, null, Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
        T(image.name, 17.sp, Modifier.padding(start = 12.dp).weight(1f), color = if (focused) FocusText else PanelText)
    }
}
