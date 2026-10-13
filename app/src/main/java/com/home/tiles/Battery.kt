package com.home.tiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BatteryInfo(val percent: Int, val charging: Boolean)

private fun readBattery(context: android.content.Context): BatteryInfo? {
    // This projector reports present=false through Android; its real battery is exposed by the vendor API.
    readXgimiBattery()?.let { return it }
    val status = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return null
    if (!status.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, false)) return null
    val level = status.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return BatteryInfo(level * 100 / scale, status.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0)
}

private fun readXgimiBattery(): BatteryInfo? = runCatching {
    val cls = Class.forName("com.xgimi.gmpf.api.PowerManager")
    val manager = cls.getMethod("getInstance").invoke(null)
    val level = (cls.getMethod("getBatteryLevel").invoke(manager) as Number).toInt()
    val charging = cls.getMethod("isAdapterPowered").invoke(manager) as? Boolean ?: false
    if (level in 0..100) BatteryInfo(level, charging) else null
}.getOrNull()

@Composable
private fun rememberBattery(): State<BatteryInfo?> {
    val context = androidx.compose.ui.platform.LocalContext.current
    return produceState< BatteryInfo? >(null, context) {
        while (true) {
            value = withContext(Dispatchers.IO) { readBattery(context) }
            delay(60_000)
        }
    }
}

/** Switch-style battery readout for the top bar; hidden when the device has no battery. */
@Composable
fun BatteryIndicator(textSize: TextUnit = StatusTextSize, color: Color = Colors.Text) {
    val battery by rememberBattery()
    val info = battery ?: return
    val low = info.percent <= 15 && !info.charging
    val tint = if (low) Color(0xFFE53935) else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        BatteryGlyph(info, tint)
        Spacer(Modifier.width(8.dp))
        T("${info.percent}%", textSize, color = tint, weight = FontWeight.Light)
    }
}

/** Horizontal battery like the Switch's: outline, fill proportional to charge, bolt when charging. */
@Composable
private fun BatteryGlyph(info: BatteryInfo, tint: Color) {
    // Same height as the Wi-Fi glyph's visible part, so the two icons line up.
    Box(Modifier.size(width = 40.dp, height = 20.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 2.5.dp.toPx()
            val nub = 3.dp.toPx()
            val body = Size(size.width - nub - stroke, size.height - stroke)
            val corner = CornerRadius(4.dp.toPx())
            drawRoundRect(tint, Offset(stroke / 2, stroke / 2), body, corner, style = Stroke(stroke))
            drawRoundRect(
                tint,
                Offset(size.width - nub, size.height * 0.32f),
                Size(nub, size.height * 0.36f),
                CornerRadius(1.5.dp.toPx()),
            )
            val inset = stroke + 2.dp.toPx()
            val fillWidth = (body.width - inset * 2 + stroke) * (info.percent.coerceIn(0, 100) / 100f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    tint,
                    Offset(inset, inset),
                    Size(fillWidth, size.height - inset * 2),
                    CornerRadius(2.dp.toPx()),
                )
            }
        }
        if (info.charging) {
            // Knocked out against the fill so it stays visible at any charge level.
            Image(
                Icons.Rounded.Bolt,
                null,
                Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(if (info.percent > 45) Colors.Background else tint),
            )
        }
    }
}
