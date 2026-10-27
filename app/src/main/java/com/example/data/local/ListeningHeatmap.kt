package com.example.data.local

/**
 * The maths behind **Peta Panas Dengar** — a GitHub-style contribution
 * heatmap of the last 365 listening days, plus the streak numbers that make
 * it worth looking at. Pure Kotlin (no Android types) so every rule here is
 * unit-testable on the JVM.
 *
 * Time is handled as **epoch days** (`playedAt / 86_400_000`), computed once
 * by SQLite in [com.example.data.local.NoxaDao.observePlaysPerDay] and again
 * here for the "today" anchor — the heatmap never needs `java.time` (which
 * would require API 26 or desugaring).
 */
object ListeningHeatmap {

    const val DAY_MS = 86_400_000L

    /** Rolling window shown by the profile card. */
    const val SPAN_DAYS = 365L

    /** Epoch day (UTC) containing [playedAt]. */
    fun dayIndex(playedAt: Long): Long = Math.floorDiv(playedAt, DAY_MS)

    /** 0 = Sunday … 6 = Saturday (epoch day 0 = Thursday 1970-01-01). */
    fun weekdayOf(dayIndex: Long): Int = Math.floorMod(dayIndex + 4, 7).toInt()

    /** 0 = January … 11 = December, in UTC. */
    fun monthOf(dayIndex: Long, calendar: java.util.Calendar): Int {
        calendar.timeInMillis = dayIndex * DAY_MS
        return calendar.get(java.util.Calendar.MONTH)
    }

    /**
     * Builds the [SPAN_DAYS] window ending at [todayDayIndex] from the
     * per-day counts in [dayPlays] (epoch day → play count).
     */
    fun build(dayPlays: Map<Long, Int>, todayDayIndex: Long): HeatmapStats {
        val calendar = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
        val start = todayDayIndex - (SPAN_DAYS - 1)
        val cells = ArrayList<HeatmapCell>(SPAN_DAYS.toInt())
        var total = 0
        for (day in start..todayDayIndex) {
            val plays = (dayPlays[day] ?: 0).coerceAtLeast(0)
            total += plays
            cells += HeatmapCell(
                dayIndex = day,
                weekday = weekdayOf(day),
                month = monthOf(day, calendar),
                plays = plays
            )
        }

        // Longest run of consecutive days with at least one play.
        var longest = 0L
        var run = 0L
        cells.forEach { cell ->
            if (cell.plays > 0) {
                run += 1
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }

        // Current streak: walk backwards from today until a silent day.
        var current = 0L
        for (i in cells.indices.reversed()) {
            if (cells[i].plays > 0) current += 1 else break
        }

        return HeatmapStats(
            cells = cells,
            totalPlays = total,
            activeDays = cells.count { it.plays > 0 }.toLong(),
            currentStreak = current,
            longestStreak = longest,
            bestDayPlays = cells.maxOf { it.plays }
        )
    }
}

/** One square of the heatmap grid. */
data class HeatmapCell(
    val dayIndex: Long,
    /** 0 = Sunday … 6 = Saturday — the row in the grid. */
    val weekday: Int,
    /** 0 = January … 11 = December — used for the month labels. */
    val month: Int,
    val plays: Int
)

/** Everything the heatmap card (and the wrapped card) needs to render. */
data class HeatmapStats(
    val cells: List<HeatmapCell>,
    val totalPlays: Int,
    val activeDays: Long,
    val currentStreak: Long,
    val longestStreak: Long,
    val bestDayPlays: Int
)
