package com.home.tiles

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal data class HdmiInput(val id: String, val label: String)

private fun hdmiIntent(input: HdmiInput, vendor: Boolean): Intent {
    val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
    return if (vendor) Intent("com.xgimi.action.hdmiPlayer", uri).setPackage("com.xgimi.xhplayer")
    else Intent(Intent.ACTION_VIEW, uri)
}

private fun Context.canOpenHdmi(input: HdmiInput): Boolean = listOf(true, false).any {
    val activity = packageManager.resolveActivity(hdmiIntent(input, it), 0)?.activityInfo
    activity?.exported == true && activity.enabled
}

/** Only connected physical ports are visible; CEC children never duplicate the port. */
private fun Context.hdmiInputs(): List<HdmiInput> = runCatching {
    val manager = getSystemService(TvInputManager::class.java) ?: return emptyList()
    val ports = manager.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
    ports.mapIndexed { index, port ->
        HdmiInput(port.id, if (ports.size == 1) "HDMI" else "HDMI ${index + 1}")
    }.filter { manager.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED && canOpenHdmi(it) }
}.getOrDefault(emptyList())

@Composable
internal fun rememberHdmiInputs(resumeTick: Int): State<List<HdmiInput>> {
    val context = LocalContext.current
    val state = remember(context) { mutableStateOf(context.hdmiInputs()) }
    DisposableEffect(context, resumeTick) {
        val manager = context.getSystemService(TvInputManager::class.java)
        fun refresh() { state.value = context.hdmiInputs() }
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputAdded(inputId: String) { state.value = context.hdmiInputs() }
            override fun onInputRemoved(inputId: String) { state.value = context.hdmiInputs() }
            override fun onInputStateChanged(inputId: String, state: Int) {
                refresh()
            }
            override fun onInputUpdated(inputId: String) { state.value = context.hdmiInputs() }
        }
        state.value = context.hdmiInputs()
        manager?.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterCallback(callback) }
    }
    return state
}

internal fun Context.openHdmi(input: HdmiInput) {
    // This firmware's generic TV activity opens a black screen; use its stock HDMI player first.
    for (vendor in listOf(true, false)) {
        if (runCatching {
            startActivity(hdmiIntent(input, vendor).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess) return
    }
    Toast.makeText(this, tr(R.string.cant_open), Toast.LENGTH_SHORT).show()
}
