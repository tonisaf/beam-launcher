package com.home.tiles

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleTvChannelsTest {
    private fun item(n: Int) = TvItem("i$n", "pkg", "title $n", "", null, null)
    private fun channel(key: String, items: Int) = TvChannel(key, key, List(items) { item(it) })

    private val channels = listOf(channel("watchnext", 2), channel("empty", 0), channel("a", 1), channel("b", 3), channel("c", 1))

    @Test
    fun offShowsNoRows() {
        assertEquals(emptyList<String>(), googleTvChannels(channels, SECOND_ROW_OFF).map { it.key })
    }

    @Test
    fun autoShowsTheFirstFewWithContent() {
        assertEquals(listOf("watchnext", "a", "b"), googleTvChannels(channels, SECOND_ROW_AUTO).map { it.key })
    }

    @Test
    fun aChosenChannelShowsAlone() {
        assertEquals(listOf("b"), googleTvChannels(channels, "b").map { it.key })
    }

    @Test
    fun aChosenChannelThatIsGoneOrEmptyShowsNothing() {
        assertEquals(emptyList<String>(), googleTvChannels(channels, "missing").map { it.key })
        assertEquals(emptyList<String>(), googleTvChannels(channels, "empty").map { it.key })
    }

    @Test
    fun sameNamedChannelsRemainDistinctAndOldSelectionStillWorks() {
        val first = TvChannel("preview:1", "History", listOf(item(1)), "pkg|History")
        val second = TvChannel("preview:2", "History", listOf(item(2)), "pkg|History")
        val sameNamed = listOf(first, second)
        assertEquals(listOf("preview:1", "preview:2"), googleTvChannels(sameNamed, SECOND_ROW_AUTO).map { it.key })
        assertEquals(first, pickSecondRow(sameNamed, "pkg|History"))
        assertEquals(second, pickSecondRow(sameNamed, "preview:2"))
    }
}
