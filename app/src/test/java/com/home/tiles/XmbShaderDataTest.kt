package com.home.tiles

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class XmbShaderDataTest {
    @Test
    fun packedAndInterpolatedCrestsStayWithinATenthOfAPixelAt4k() {
        val data = XmbShaderData(3840, 2160)
        for (time in listOf(0f, 1f, 37f, 3600f)) {
            data.update(time)
            for (wave in 0..2) {
                for (step in 0..2048) {
                    val u = step / 2048f
                    val sample = u * (XmbShaderData.CREST_WIDTH - 1)
                    val left = sample.toInt()
                    val right = (left + 1).coerceAtMost(XmbShaderData.CREST_WIDTH - 1)
                    val fraction = sample - left
                    val height = decode(data, left, wave) * (1f - fraction) + decode(data, right, wave) * fraction
                    val errorPixels = abs(height - xmbCrest(u, time, wave)) * 2160f
                    assertTrue("wave=$wave time=$time u=$u error=$errorPixels", errorPixels < 0.1f)
                }
            }
        }
    }

    @Test
    fun sparklesPreserveTheirCellPositionsBrightnessAndEmptyCells() {
        val data = XmbShaderData(1920, 1080)
        var visible = 0
        var empty = 0
        for (time in listOf(0f, 1f, 37f)) {
            data.update(time)
            for (index in data.sparklePixels.indices) {
                val x = index % data.columns
                val y = index / data.columns
                val hash = sin(x * 12.9898f + y * 78.233f) * 43758.5453f
                val seed = hash - floor(hash)
                val pixel = data.sparklePixels[index]
                assertEquals(255, pixel ushr 24)
                val actualX = ((pixel ushr 16) and 255) / 255f
                val actualY = ((pixel ushr 8) and 255) / 255f
                assertEquals(0.5f + 0.35f * sin(time * 0.25f + seed * 6.28f), actualX, 0.5f / 255f + 0.00001f)
                assertEquals(0.5f + 0.35f * cos(time * 0.2f + seed * 9f), actualY, 0.5f / 255f + 0.00001f)
                val brightness = (pixel and 255) / 255f
                if (seed < 0.9f) {
                    assertEquals(0f, brightness, 0f)
                    empty++
                } else {
                    assertEquals(0.5f + 0.5f * sin(time * 1.3f + seed * 40f), brightness, 0.5f / 255f + 0.00001f)
                    visible++
                }
            }
        }
        assertTrue(visible > 0)
        assertTrue(empty > visible)
    }

    @Test
    fun resizedViewportsIncludePartialEdgeCellsAndFramesReuseBuffers() {
        val data = XmbShaderData(71, 141)
        assertEquals(2, data.columns)
        assertEquals(3, data.rows)
        val crests = data.crestPixels
        val sparkles = data.sparklePixels
        data.update(0f)
        val first = crests.copyOf()
        data.update(1f)
        assertSame(crests, data.crestPixels)
        assertSame(sparkles, data.sparklePixels)
        assertTrue(!first.contentEquals(crests))
        assertEquals(1, XmbShaderData(0, 0).columns)
        assertEquals(1, XmbShaderData(0, 0).rows)
    }

    private fun decode(data: XmbShaderData, sample: Int, wave: Int): Float {
        val shift = 16 - wave * 8
        val high = (data.crestPixels[sample] ushr shift) and 255
        val low = (data.crestPixels[sample + XmbShaderData.CREST_WIDTH] ushr shift) and 255
        return (high * 256 + low) / 65535f
    }
}
