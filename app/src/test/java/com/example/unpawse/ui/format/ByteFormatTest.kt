package com.example.unpawse.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {

    @Test
    fun `bytes below a kilobyte are shown raw`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `kilobytes and megabytes use binary units`() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }

    /** Small values keep a decimal because "9 MB" hides a third of the real size; large ones drop it. */
    @Test
    fun `the decimal is dropped once the number is big enough not to need it`() {
        assertEquals("9.5 MB", formatBytes((9.5 * 1024 * 1024).toLong()))
        assertEquals("38 MB", formatBytes((38.4 * 1024 * 1024).toLong()))
    }

    @Test
    fun `gigabytes are the largest unit, so terabyte-scale values stay in GB`() {
        assertEquals("1024 GB", formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `a negative size is clamped rather than rendered as nonsense`() {
        assertEquals("0 B", formatBytes(-1))
    }
}
