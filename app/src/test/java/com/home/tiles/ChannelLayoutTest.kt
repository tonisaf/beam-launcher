package com.home.tiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelLayoutTest {
    private fun channel(key: String, legacy: String = key, empty: Boolean = false) =
        TvChannel(key, key, if (empty) emptyList() else listOf(TvItem("item:$key", "pkg", "title", "", null, null)), legacy)

    @Test fun defaultsPutFirstAboveAndAllOthersBelow() {
        val rows = channelPlacements(listOf(channel("a"), channel("b"), channel("c"), channel("d")), null, SECOND_ROW_AUTO)
        assertEquals(listOf(ChannelPosition.ABOVE, ChannelPosition.BELOW, ChannelPosition.BELOW, ChannelPosition.BELOW), rows.map { it.position })
    }

    @Test fun preservesHiddenEmptyRowsAndAddsNewAppsBelow() {
        val saved = listOf(ChannelRule("a", "a", ChannelPosition.HIDDEN), ChannelRule("b", "b", ChannelPosition.ABOVE))
        val rows = channelPlacements(listOf(channel("a"), channel("b", empty = true), channel("new")), saved, SECOND_ROW_OFF)
        assertEquals(listOf(ChannelPosition.HIDDEN, ChannelPosition.ABOVE, ChannelPosition.BELOW), rows.map { it.position })
    }

    @Test fun movesWithinGroupWithoutMovingOtherGroup() {
        val rows = listOf(ChannelPlacement(channel("a"), ChannelPosition.BELOW), ChannelPlacement(channel("b"), ChannelPosition.ABOVE), ChannelPlacement(channel("c"), ChannelPosition.BELOW))
        assertEquals(listOf("c", "b", "a"), moveChannel(rows, "c", -1).map { it.channel.key })
        assertEquals(rows, moveChannel(rows, "b", -1))
    }

    @Test fun restoresRecreatedChannelAndRetainsUninstalledAppRules() {
        val saved = listOf(ChannelRule("old", "pkg|history", ChannelPosition.HIDDEN), ChannelRule("gone", "gone", ChannelPosition.ABOVE))
        val rows = channelPlacements(listOf(channel("new", "pkg|history")), saved, SECOND_ROW_AUTO)
        assertEquals(ChannelPosition.HIDDEN, rows.single().position)
        assertEquals(listOf("new", "gone"), placementsToRules(rows, saved).map { it.key })
    }

    @Test fun duplicateNamesDoNotApplyRuleTwice() {
        val rows = channelPlacements(listOf(channel("1", "same"), channel("2", "same")), listOf(ChannelRule("1", "same", ChannelPosition.HIDDEN)), SECOND_ROW_AUTO)
        assertEquals(listOf(ChannelPosition.HIDDEN, ChannelPosition.BELOW), rows.map { it.position })
    }

    @Test fun missingDuplicateCannotStealAnotherRowsExactRule() {
        val saved = listOf(ChannelRule("gone", "same", ChannelPosition.HIDDEN), ChannelRule("2", "same", ChannelPosition.ABOVE))
        val rows = channelPlacements(listOf(channel("2", "same")), saved, SECOND_ROW_AUTO)
        assertEquals(ChannelPosition.ABOVE, rows.single().position)
        assertEquals(listOf("2", "gone"), placementsToRules(rows, saved).map { it.key })
    }

    @Test fun emptyAutoChannelIsEnabledWhenItGetsContent() {
        val rows = channelPlacements(listOf(channel("empty", empty = true), channel("active")), null, SECOND_ROW_AUTO)
        assertEquals(listOf(ChannelPosition.BELOW, ChannelPosition.ABOVE), rows.map { it.position })
    }
}
