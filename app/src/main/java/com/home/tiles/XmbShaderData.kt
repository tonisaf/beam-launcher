package com.home.tiles

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Reused CPU-side lookup buffers: trigonometry runs per sample/cell, never per fragment. */
internal class XmbShaderData(width: Int, height: Int) {
    val columns: Int = ceil(width.coerceAtLeast(1) / CELL_SIZE).toInt()
    val rows: Int = ceil(height.coerceAtLeast(1) / CELL_SIZE).toInt()
    // RGB holds the three crests; rows 0/1 hold the high/low bytes of each 16-bit height.
    val crestPixels: IntArray = IntArray(CREST_WIDTH * 2)
    // RGB holds the cell-relative x/y and brightness; opaque alpha keeps the bytes unmodified.
    val sparklePixels: IntArray = IntArray(columns * rows)
    private val seeds: FloatArray = FloatArray(columns * rows) { index ->
        val x = index % columns
        val y = index / columns
        val hash = sin(x * 12.9898f + y * 78.233f) * 43758.5453f
        hash - floor(hash)
    }

    fun update(time: Float) {
        for (sample in 0 until CREST_WIDTH) {
            val u = sample / (CREST_WIDTH - 1f)
            val first = encodeCrest(xmbCrest(u, time, 0))
            val second = encodeCrest(xmbCrest(u, time, 1))
            val third = encodeCrest(xmbCrest(u, time, 2))
            crestPixels[sample] = rgb(first shr 8, second shr 8, third shr 8)
            crestPixels[sample + CREST_WIDTH] = rgb(first and 255, second and 255, third and 255)
        }
        for (index in seeds.indices) {
            val seed = seeds[index]
            val x = 0.5f + 0.35f * sin(time * 0.25f + seed * 6.28f)
            val y = 0.5f + 0.35f * cos(time * 0.2f + seed * 9f)
            val brightness = if (seed >= 0.9f) 0.5f + 0.5f * sin(time * 1.3f + seed * 40f) else 0f
            sparklePixels[index] = rgb(encodeByte(x), encodeByte(y), encodeByte(brightness))
        }
    }

    private fun encodeCrest(value: Float): Int = (value.coerceIn(0f, 1f) * 65535f).roundToInt()
    private fun encodeByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()
    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (255 shl 24) or (red shl 16) or (green shl 8) or blue

    companion object {
        const val CREST_WIDTH = 129
        const val CELL_SIZE = 70f
    }
}

/** Shared by the pre-Android 13 renderer and the shader lookup generator. */
internal fun xmbCrest(u: Float, time: Float, index: Int): Float {
    val wave = index.toFloat()
    return 0.56f + 0.045f * wave +
        0.075f * sin(u * (2.4f + 0.7f * wave) + time * (0.16f + 0.05f * wave) + wave * 1.9f) +
        0.03f * sin(u * (6.5f + wave) - time * (0.22f + 0.04f * wave))
}
