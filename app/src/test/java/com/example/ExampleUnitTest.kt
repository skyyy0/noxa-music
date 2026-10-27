package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun formatDuration_formatsMinutesAndSeconds() {
    assertEquals("03:34", com.example.ui.util.FormatUtils.formatDuration(214000L))
    assertEquals("00:00", com.example.ui.util.FormatUtils.formatDuration(0L))
    assertEquals("01:05", com.example.ui.util.FormatUtils.formatDuration(65000L))
  }

  @Test
  fun formatViews_formatsViewsNicely() {
    assertEquals("1.5M views", com.example.ui.util.FormatUtils.formatViews("1500000"))
    assertEquals("24.0K views", com.example.ui.util.FormatUtils.formatViews("24000"))
    assertEquals("500 views", com.example.ui.util.FormatUtils.formatViews("500"))
  }
}
