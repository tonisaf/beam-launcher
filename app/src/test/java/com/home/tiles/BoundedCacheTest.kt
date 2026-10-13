package com.home.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedCacheTest {
    @Test fun evictsLeastRecentlyUsedByWeight() {
        val cache = BoundedCache<String, String>(6) { it.length }
        cache["a"] = "aaa"
        cache["b"] = "bbb"
        assertEquals("aaa", cache["a"])
        cache["c"] = "ccc"
        assertNull(cache["b"])
        assertEquals("aaa", cache["a"])
        assertEquals("ccc", cache["c"])
    }

    @Test fun replacementsAndOversizedImagesRespectBudget() {
        val cache = BoundedCache<String, String>(4) { it.length }
        cache["a"] = "aaa"
        cache["a"] = "a"
        cache["b"] = "bbb"
        cache["large"] = "12345"
        assertEquals("a", cache["a"])
        assertEquals("bbb", cache["b"])
        assertNull(cache["large"])
    }
}
