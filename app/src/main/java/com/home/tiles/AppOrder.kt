package com.home.tiles

/** Favourites are a separate leading group. Moving a tile never changes membership. */
internal fun moveApp(order: List<AppEntry>, pkg: String, direction: Int): List<AppEntry> {
    val index = order.indexOfFirst { it.pkg == pkg }
    if (index < 0) return order
    val target = index + direction
    if (target !in order.indices || order[target].pinned != order[index].pinned) return order
    return order.toMutableList().apply { val other = this[target]; this[target] = this[index]; this[index] = other }
}

internal fun sortApps(entries: List<AppEntry>, pins: List<String>, manualOrder: List<String>?): List<AppEntry> =
    entries.sortedWith(compareBy<AppEntry> { if (it.pinned) pins.indexOf(it.pkg).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE else Int.MAX_VALUE }
        .thenBy { if (manualOrder == null) 0 else manualOrder.indexOf(it.pkg).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        .thenByDescending { if (manualOrder == null) it.lastUsed else 0L }
        .thenBy { it.label.lowercase() })
