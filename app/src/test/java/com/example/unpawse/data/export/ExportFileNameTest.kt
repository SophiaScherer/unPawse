package com.example.unpawse.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ExportFileNameTest {

    @Test
    fun `the default name is dated so repeat exports do not collide`() {
        assertEquals(
            "unpawse-export-2026-07-27.zip",
            ExportRepository.defaultFileName(LocalDate.of(2026, 7, 27)),
        )
    }

    @Test
    fun `the date comes from the given zone, not UTC`() {
        // Late on the 27th in UTC is already the 28th in Tokyo (+9).
        val millis = ZonedDateTime.of(2026, 7, 27, 23, 30, 0, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        assertEquals(
            "unpawse-export-2026-07-28.zip",
            ExportRepository.defaultFileName(millis, ZoneId.of("Asia/Tokyo")),
        )
        assertEquals(
            "unpawse-export-2026-07-27.zip",
            ExportRepository.defaultFileName(millis, ZoneId.of("UTC")),
        )
    }
}
