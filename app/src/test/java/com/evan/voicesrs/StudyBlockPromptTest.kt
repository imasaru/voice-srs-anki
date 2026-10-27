package com.evan.voicesrs

import org.junit.Test
import java.time.ZoneId

class StudyBlockPromptTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun at(h: Int, m: Int): Long =
        java.time.ZonedDateTime.of(2026, 10, 27, h, m, 0, 0, zone).toInstant().toEpochMilli()

}
