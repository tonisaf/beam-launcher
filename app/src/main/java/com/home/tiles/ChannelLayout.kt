package com.home.tiles

enum class ChannelPosition { ABOVE, BELOW, HIDDEN }
data class ChannelRule(val key: String, val legacyKey: String, val position: ChannelPosition)
data class ChannelPlacement(val channel: TvChannel, val position: ChannelPosition, val ruleKey: String? = null)

/** Saved order survives empty rows; new channels appear below apps in provider order. */
fun channelPlacements(channels: List<TvChannel>, rules: List<ChannelRule>?, legacySetting: String): List<ChannelPlacement> {
    if (rules == null) {
        val visible = googleTvChannels(channels, legacySetting).map { it.key }.toSet()
        var first = true
        return channels.map {
            val position = if (legacySetting != SECOND_ROW_AUTO && it.key !in visible) ChannelPosition.HIDDEN else if (first && it.items.isNotEmpty()) {
                first = false
                ChannelPosition.ABOVE
            } else ChannelPosition.BELOW
            ChannelPlacement(it, position)
        }
    }
    val remaining = channels.toMutableList()
    val exactKeys = rules.map { it.key }.toSet()
    val result = mutableListOf<ChannelPlacement>()
    for (rule in rules) {
        val channel = remaining.firstOrNull { it.key == rule.key }
            ?: remaining.firstOrNull { it.legacyKey == rule.legacyKey && it.key !in exactKeys }
            ?: continue
        remaining.remove(channel)
        result += ChannelPlacement(channel, rule.position, rule.key)
    }
    result += remaining.map { ChannelPlacement(it, ChannelPosition.BELOW) }
    return result
}

fun placementsToRules(placements: List<ChannelPlacement>, saved: List<ChannelRule>?): List<ChannelRule> =
    placements.map { ChannelRule(it.channel.key, it.channel.legacyKey, it.position) } +
        saved.orEmpty().filter { rule -> placements.none { it.channel.key == rule.key || it.ruleKey == rule.key } }

fun moveChannel(placements: List<ChannelPlacement>, key: String, direction: Int): List<ChannelPlacement> {
    val result = placements.toMutableList()
    val index = result.indexOfFirst { it.channel.key == key }
    if (index < 0) return result
    val peers = result.indices.filter { result[it].position == result[index].position }
    val peer = peers.indexOf(index)
    val target = peers.getOrNull(peer + direction) ?: return result
    val other = result[target]
    result[target] = result[index]
    result[index] = other
    return result
}
