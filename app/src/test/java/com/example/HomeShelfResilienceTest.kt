package com.example

import com.example.data.remote.safeShelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the v1.11 force-close: the home feed used to be built
 * fail-fast, so any exception inside a single shelf escaped
 * `MusicViewModel.loadHome` and killed the app while Home still showed
 * "Memuat musik...". Every shelf now runs through [safeShelf], which must
 * swallow provider failures while still propagating cancellation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeShelfResilienceTest {

    @Test
    fun `a failing shelf degrades to null instead of throwing`() = runBlocking {
        val result = safeShelf<Int> { throw IllegalStateException("provider down") }
        assertNull(result)
    }

    @Test
    fun `a healthy shelf returns its value unchanged`() = runBlocking {
        assertEquals(42, safeShelf { 42 })
    }

    @Test
    fun `cancellation still propagates out of a shelf`() = runBlocking {
        var cancelled = false
        try {
            safeShelf<Int> { throw CancellationException("scope cancelled") }
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue("safeShelf must rethrow CancellationException", cancelled)
    }
}
