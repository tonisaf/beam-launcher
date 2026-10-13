package com.home.tiles

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    private fun app(pkg: String, pinned: Boolean = false, used: Long = 0) =
        AppEntry(pkg, ComponentName(pkg, "Main"), pkg, used, false, pinned, false, 0)

    @Test fun manualOrderIgnoresLaunchTimesAndAppendsNewApps() {
        val apps = listOf(app("new"), app("b", used = 999), app("a", used = 1))
        assertEquals(listOf("a", "b", "new"), sortApps(apps, emptyList(), listOf("a", "b")).map { it.pkg })
    }

    @Test fun favouritesStayFirstInSavedOrder() {
        val apps = listOf(app("a", used = 999), app("b", pinned = true), app("c", pinned = true))
        assertEquals(listOf("c", "b", "a"), sortApps(apps, listOf("c", "b"), listOf("a", "b", "c")).map { it.pkg })
    }

    @Test fun movingCannotCrossFavouriteBoundary() {
        val apps = listOf(app("p", true), app("a"), app("b"))
        assertEquals(apps, moveApp(apps, "a", -1))
        assertEquals(apps, moveApp(apps, "p", 1))
        assertEquals(listOf("p", "b", "a"), moveApp(apps, "b", -1).map { it.pkg })
        assertEquals(apps, moveApp(apps, "b", 1))
    }

    @Test fun installedAppsRecoverTheirSavedPlaceAfterReturning() {
        val order = listOf("a", "gone", "b")
        assertEquals(listOf("a", "b"), sortApps(listOf(app("b"), app("a")), emptyList(), order).map { it.pkg })
        assertEquals(order, sortApps(listOf(app("b"), app("gone"), app("a")), emptyList(), order).map { it.pkg })
    }
}
