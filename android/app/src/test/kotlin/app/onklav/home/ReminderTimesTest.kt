package app.onklav.home

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderTimesTest {
    @Test fun calendarMorningSurvivesSpringClockChange() {
        val now = Instant.parse("2026-03-08T05:59:30Z").toEpochMilli()
        val presets = reminderPresets(now, ZoneId.of("America/Chicago")).toMap()
        assertEquals(now + 3_600_000L, presets["In an Hour"])
        assertEquals(Instant.parse("2026-03-08T14:00:00Z").toEpochMilli(), presets["Tomorrow"])
        assertFalse(presets.containsKey("Tonight"))
    }

    @Test fun lateEveningCapsTonightWithoutSchedulingPastTime() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-21T23:30:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-21T23:59:00Z").toEpochMilli(),
            reminderPresets(now, zone).toMap()["Tonight"])
        assertFalse(reminderPresets(now + 29 * 60_000L, zone).toMap().containsKey("Tonight"))
    }
}
