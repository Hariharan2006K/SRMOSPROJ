package com.voltwise.powermonitor.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatDuration() {
        assertEquals("45s", FormatUtils.formatDuration(45_000L))
        assertEquals("2m 15s", FormatUtils.formatDuration(135_000L))
        assertEquals("1h 30m", FormatUtils.formatDuration(5400_000L))
    }
}
