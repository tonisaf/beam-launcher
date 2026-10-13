package com.home.tiles

import androidx.compose.foundation.ExperimentalFoundationApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class HomeFocusScrollTest {
    @Test
    fun visibleIconsStayStillAsFocusScaleChangesTheirBounds() {
        // At TV's pivot, a 152 px icon grows to 167.2 px around its centre.
        // Both that icon and the next unscaled one already fit in the viewport.
        for ((top, height) in listOf(216f to 152f, 208.4f to 167.2f, 210f to 160f)) {
            assertEquals(0f, HomeVerticalBringIntoViewSpec.calculateScrollDistance(top, height, 720f), 0.001f)
        }
    }

    @Test
    fun enteringARowBelowStillBringsTheFocusedCardIntoView() {
        assertEquals(150f, HomeVerticalBringIntoViewSpec.calculateScrollDistance(700f, 170f, 720f), 0.001f)
    }

    @Test
    fun returningToARowAboveStillBringsTheFocusedIconIntoView() {
        assertEquals(-10f, HomeVerticalBringIntoViewSpec.calculateScrollDistance(-10f, 170f, 720f), 0.001f)
    }
}
