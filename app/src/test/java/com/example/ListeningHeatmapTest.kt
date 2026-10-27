package com.example

import com.example.data.local.ListeningHeatmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Peta Panas Dengar: window anchoring, intensity inputs, and streaks. */
class ListeningHeatmapTest {

    // Anchoring is relative: any recent epoch day exercises the same rules.
    private val today = ListeningHeatmap.dayIndex(1_792_022_400_000L) // a Thursday

    @Test
    fun `epoch day and weekday mapping`() {
        assertEquals(0L, ListeningHeatmap.dayIndex(0L)) // 1970-01-01
        assertEquals(4, ListeningHeatmap.weekdayOf(0L)) // …was a Thursday
        assertEquals(0, ListeningHeatmap.weekdayOf(3L)) // 1970-01-04 was a Sunday
        assertEquals(1, ListeningHeatmap.weekdayOf(86_400_000L * 11)) // +11 days = Monday
    }

    @Test
    fun `window is exactly 365 cells ending today`() {
        val stats = ListeningHeatmap.build(emptyMap(), today)
        assertEquals(365, stats.cells.size)
        assertEquals(today, stats.cells.last().dayIndex)
        assertEquals(today - 364, stats.cells.first().dayIndex)
        assertEquals(0, stats.totalPlays)
        assertEquals(0L, stats.activeDays)
        assertEquals(0L, stats.currentStreak)
        assertEquals(0L, stats.longestStreak)
        // Every cell's weekday matches its own epoch day (grid rows are stable).
        assertTrue(stats.cells.all { it.weekday == ListeningHeatmap.weekdayOf(it.dayIndex) })
    }

    @Test
    fun `plays outside the window are ignored`() {
        val stats = ListeningHeatmap.build(
            mapOf(
                today to 5,
                (today - 364) to 3,
                (today - 365) to 999 // one day too old
            ),
            today
        )
        assertEquals(8, stats.totalPlays)
        assertEquals(2L, stats.activeDays)
        assertEquals(5, stats.bestDayPlays)
        assertEquals(1L, stats.longestStreak) // today only; day -364 is isolated
    }

    @Test
    fun `current streak walks back from today and breaks on silence`() {
        // Today + yesterday active, day before silent, then three active.
        val dayPlays = mapOf(
            today to 1,
            (today - 1) to 2,
            (today - 3) to 1,
            (today - 4) to 1,
            (today - 5) to 1
        )
        val stats = ListeningHeatmap.build(dayPlays, today)
        assertEquals(2L, stats.currentStreak) // stops at the silent day
        assertEquals(3L, stats.longestStreak) // the older run of three
        assertEquals(6, stats.totalPlays)
    }

    @Test
    fun `streak that starts today only`() {
        val stats = ListeningHeatmap.build(mapOf(today to 4), today)
        assertEquals(1L, stats.currentStreak)
        assertEquals(1L, stats.longestStreak)
    }

    @Test
    fun `negative or missing counts never poison the totals`() {
        val stats = ListeningHeatmap.build(mapOf(today to -3), today)
        assertEquals(0, stats.totalPlays)
        assertEquals(0L, stats.activeDays)
        assertEquals(0, stats.bestDayPlays)
    }
}
