package app.onklav.home

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

/** Read-only device time and display preferences; no calendar or location access. */
internal object SdkClock {
    fun snapshot(context: Context): JSONObject {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return snapshot(Instant.now(), SystemClock.elapsedRealtime(), ZoneId.systemDefault(),
            locale, DateFormat.is24HourFormat(context))
    }

    internal fun snapshot(now: Instant, elapsedMs: Long, zone: ZoneId,
                          locale: Locale, use24Hour: Boolean): JSONObject {
        val local = now.atZone(zone)
        val first = Calendar.getInstance(locale).firstDayOfWeek
        return JSONObject().put("epoch_ms", now.toEpochMilli())
            .put("monotonic_ms", elapsedMs).put("timezone", zone.id)
            .put("offset_seconds", local.offset.totalSeconds)
            .put("local_date", local.toLocalDate().toString())
            .put("local_time", local.toLocalTime().toString())
            .put("locale", locale.toLanguageTag())
            .put("first_weekday", (first + 5) % 7 + 1)
            .put("hour_cycle", if (use24Hour) "h23" else "h12")
            .put("daylight_saving", zone.rules.isDaylightSavings(now))
    }
}
