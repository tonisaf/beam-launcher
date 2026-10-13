package com.home.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val resumeTick = mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        LauncherSettings.init(this)
        RemoteButtons.init(this)
        Sounds.init(this)
        setContent {
            LauncherApp(repo, apps.value, resumeTick.intValue, PanelRequests.count.intValue, onChanged = ::reload)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
        resumeTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: jump back to the start.
        resumeTick.intValue++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        Sounds.release()
        super.onDestroy()
    }

    private fun reload() {
        lifecycleScope.launch {
            apps.value = withContext(Dispatchers.IO) { repo.loadApps() }
        }
    }
}

/** Panel open requests from [OpenPanelActivity]; same process, so a plain counter is enough. */
object PanelRequests {
    val count = mutableIntStateOf(0)
}

/** Invisible trampoline: flags a panel request and brings the launcher forward. */
class OpenPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bring Home forward and open the launcher settings.
        LauncherSettings.init(this)
        PanelRequests.count.intValue++
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

private enum class Screen { Home, AllApps }

@Composable
private fun LauncherApp(
    repo: AppRepository,
    apps: List<AppEntry>,
    resumeTick: Int,
    panelRequest: Int,
    onChanged: () -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.Home) }
    DisposableEffect(Unit) { onDispose { WallpaperPreview.cancel() } }
    var optionsFor by remember { mutableStateOf<AppEntry?>(null) }
    var moving by remember { mutableStateOf<HomeMove?>(null) }
    var savedOrder by remember { mutableStateOf<List<String>?>(null) }
    var focusApp by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var welcomeOpen by remember { mutableStateOf(!LauncherSettings.welcomeDone) }
    var updateOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Checked again whenever the launcher comes back, so a permission granted meanwhile drops off the list.
    val missing = remember(resumeTick) { Setup.missing(context) }

    LaunchedEffect(resumeTick) { screen = Screen.Home; moving = null; focusApp = null }
    LaunchedEffect(apps) {
        if (moving?.pkg?.let { pkg -> apps.none { it.pkg == pkg && !it.hidden } } == true) moving = null
        if (apps.filter { !it.hidden }.map { it.pkg } == savedOrder) savedOrder = null
    }
    LaunchedEffect(missing, welcomeOpen) { if (!welcomeOpen && Setup.shouldAutoShow(context, missing)) setupOpen = true }
    LaunchedEffect(panelRequest) { if (panelRequest > 0) panelOpen = true }
    LaunchedEffect(resumeTick, welcomeOpen) {
        if (!welcomeOpen && Updates.check(context, automatic = true) && Updates.shouldOffer(context)) updateOpen = true
    }

    fun finishMove(save: Boolean) {
        val session = moving ?: return
        if (save) {
            repo.saveOrder(session.draft, apps)
            savedOrder = session.draft.map { it.pkg }
            onChanged()
        }
        focusApp = session.pkg
        moving = null
    }
    val homeApps = moving?.draft?.plus(apps.filter { it.hidden }) ?: savedOrder?.let { order ->
        apps.sortedBy { order.indexOf(it.pkg).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
    } ?: apps
    Box(Modifier.fillMaxSize().background(Colors.backgroundBrush()).arrowSoundTracker()
        .onPreviewKeyEvent { event ->
            val session = moving ?: return@onPreviewKeyEvent false
            val native = event.nativeKeyEvent
            when (native.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (native.action == android.view.KeyEvent.ACTION_DOWN)
                        session.move(if (native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                    if (native.action == android.view.KeyEvent.ACTION_UP) { Sounds.activate(); finishMove(true) }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_MENU -> true
                else -> false
            }
        }) {
        if (Colors.isXmb && WallpaperPreview.draft == null) XmbBackground()
        if (LauncherSettings.wallpaper != 0L || WallpaperPreview.draft != null) WallpaperBackground()
        Box {
        when (screen) {
            Screen.Home -> HomeScreen(
                repo, homeApps, resumeTick,
                onOpenAll = { screen = Screen.AllApps },
                // Launcher settings are always hosted here, without an accessibility overlay.
                onOpenPanel = { panelOpen = true },
                onOptions = { optionsFor = it },
                onSetup = if (missing.isNotEmpty()) { { setupOpen = true } } else null,
                moving = moving,
                focusApp = moving?.pkg ?: focusApp,
            )
            Screen.AllApps -> AllAppsScreen(
                repo, apps,
                onBack = { screen = Screen.Home },
                onOptions = { optionsFor = it },
            )
        }
        }
        if (panelOpen && WallpaperPreview.draft == null) SettingsPanel(onDismiss = { panelOpen = false })
        if (WallpaperPreview.draft != null) WallpaperPreviewControls()
        if (welcomeOpen) WelcomeDialog(onDismiss = { welcomeOpen = false })
        if (updateOpen && !welcomeOpen && !setupOpen && !panelOpen && moving == null) UpdateDialog(onDismiss = { updateOpen = false })
        if (setupOpen && missing.isNotEmpty()) SetupDialog(missing, onDismiss = { setupOpen = false })
        optionsFor?.let { entry ->
            OptionsDialog(entry, repo, onDismiss = { optionsFor = null }, onChanged = onChanged,
                onMove = {
                    screen = Screen.Home
                    moving = HomeMove(homeApps.filter { !it.hidden }, entry.pkg)
                })
        }
        moving?.let { session ->
            MoveHint(session, Modifier.align(Alignment.BottomCenter), onSave = { finishMove(true) }, onCancel = { finishMove(false) })
        }
    }
    BackHandler(enabled = moving != null) { finishMove(false) }
}
