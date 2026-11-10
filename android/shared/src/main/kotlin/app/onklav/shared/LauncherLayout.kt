package app.onklav.shared

/** Immutable sparse launcher positions; empty cells are part of the user's layout. */
data class LauncherLayout private constructor(val slots: List<String>) {
    val pages: Int
        get() = slots.size / PAGE_SIZE

    fun ensurePage(page: Int): LauncherLayout {
        if (page !in 0 until MAX_PAGES || page < pages) return this
        return LauncherLayout(slots + List((page + 1) * PAGE_SIZE - slots.size) { "" })
    }

    fun move(id: String, destination: Int): LauncherLayout {
        val source = slots.indexOf(id)
        if (id.isEmpty() || source < 0 || destination !in slots.indices || source == destination)
            return this
        val next = slots.toMutableList()
        if (next[destination].isEmpty()) {
            next[source] = ""
            next[destination] = id
        } else if (source / PAGE_SIZE == destination / PAGE_SIZE) {
            // Reorder within this page only; the source itself provides the free slot.
            next.add(destination, next.removeAt(source))
        } else {
            next[source] = ""
            val pageStart = destination / PAGE_SIZE * PAGE_SIZE
            val pageEnd = pageStart + PAGE_SIZE
            // Prefer space after the drop, then space before it on the same page.
            var hole = (destination until pageEnd).firstOrNull { next[it].isEmpty() }
                ?: (destination - 1 downTo pageStart).firstOrNull { next[it].isEmpty() }
            if (hole == null) {
                // Only a completely full destination page may spill into following pages.
                hole = (pageEnd until next.size).firstOrNull { next[it].isEmpty() }
                if (hole == null) {
                    if (next.size >= MAX_PAGES * PAGE_SIZE) return this
                    hole = next.size
                    next.addAll(List(PAGE_SIZE) { "" })
                }
            }
            if (hole > destination) for (i in hole downTo destination + 1) next[i] = next[i - 1]
            else for (i in hole until destination) next[i] = next[i + 1]
            next[destination] = id
        }
        return LauncherLayout(next)
    }

    fun firstSlot(page: Int): Int =
        (page * PAGE_SIZE until (page + 1) * PAGE_SIZE).firstOrNull { slots.getOrNull(it) == "" }
            ?: page * PAGE_SIZE

    fun trimmed(): LauncherLayout {
        val last = slots.indexOfLast { it.isNotEmpty() }
        return LauncherLayout(slots.take(maxOf(PAGE_SIZE, (last / PAGE_SIZE + 1) * PAGE_SIZE)))
    }

    fun encode(): String = "g1|" + trimmed().slots.joinToString(",")

    companion object {
        const val COLUMNS = 3
        const val ROWS = 5
        const val PAGE_SIZE = COLUMNS * ROWS
        const val MAX_PAGES = 64

        fun read(encoded: String, catalog: List<String>): LauncherLayout {
            require(
                catalog.distinct().size == catalog.size && catalog.size <= MAX_PAGES * PAGE_SIZE
            )
            require(catalog.all { it.matches(Regex("[a-z][a-z0-9_-]{0,64}")) })
            val raw =
                if (encoded.startsWith("g1|") && encoded.length <= 49152)
                    encoded.removePrefix("g1|").split(',').take(MAX_PAGES * PAGE_SIZE)
                else emptyList()
            val seen = mutableSetOf<String>()
            val cells =
                MutableList(
                    maxOf(PAGE_SIZE, ((raw.size + PAGE_SIZE - 1) / PAGE_SIZE) * PAGE_SIZE)
                ) { index ->
                    raw.getOrNull(index)?.takeIf { it in catalog && seen.add(it) } ?: ""
                }
            for (id in catalog.filterNot { it in seen }) {
                if ("" !in cells) cells.addAll(List(PAGE_SIZE) { "" })
                cells[cells.indexOf("")] = id
            }
            return LauncherLayout(cells).trimmed()
        }

        fun destination(
            page: Int,
            x: Float,
            y: Float,
            width: Float,
            cellHeight: Float,
            scroll: Float,
        ): Int =
            page * PAGE_SIZE +
                ((y + scroll) / cellHeight.coerceAtLeast(1f)).toInt().coerceIn(0, ROWS - 1) *
                    COLUMNS +
                (x * COLUMNS / width.coerceAtLeast(1f)).toInt().coerceIn(0, COLUMNS - 1)
    }
}
