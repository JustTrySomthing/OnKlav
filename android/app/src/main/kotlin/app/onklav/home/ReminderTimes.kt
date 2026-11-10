package app.onklav.home

import java.time.Instant
import java.time.ZoneId

/** Elapsed hour; calendar-based evening and next morning in the user's zone. */
internal fun reminderPresets(now: Long, zone: ZoneId): List<Pair<String, Long>> {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val evening = local.toLocalDate().atTime(20, 0).atZone(zone)
    val tonight = (if (evening.isAfter(local)) evening else local.plusHours(1).withSecond(0).withNano(0))
        .let { minOf(it, local.toLocalDate().atTime(23, 59).atZone(zone)) }
        .toInstant().toEpochMilli()
    return buildList {
        add("In an Hour" to now + 3_600_000L)
        if (tonight >= now + 60_000L) add("Tonight" to tonight)
        add("Tomorrow" to local.toLocalDate().plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
    }
}
