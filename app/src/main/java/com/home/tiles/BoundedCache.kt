package com.home.tiles

/** Access-ordered cache; eviction drops references without recycling images still on screen. */
internal class BoundedCache<K, V>(private val budget: Int, private val weight: (V) -> Int) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var used = 0

    @Synchronized operator fun get(key: K): V? = entries[key]

    @Synchronized operator fun set(key: K, value: V) {
        entries.remove(key)?.let { used -= weight(it) }
        val size = weight(value)
        if (size > budget) return
        entries[key] = value
        used += size
        val iterator = entries.entries.iterator()
        while (used > budget && iterator.hasNext()) {
            used -= weight(iterator.next().value)
            iterator.remove()
        }
    }
}
