# Device clock (working source API)

Call `onklav_ui::clock::read("clock_ready")`. Handle the ordinary
`event.reply.ok`, `event.reply.value` or `event.reply.error` response.
No capability or Android permission is required. The app must still be installed
and its session current. This service cannot set the clock, read calendar entries,
access location or schedule reminders.

`clock.read` accepts exactly `{}` and returns one current snapshot:

| Field | Meaning |
| --- | --- |
| epoch_ms | Device wall-clock milliseconds since Unix epoch; user adjustable |
| monotonic_ms | Elapsed milliseconds since boot, including sleep; resets at reboot |
| timezone | Current device zone ID |
| offset_seconds | UTC offset at the sampled instant, including DST |
| daylight_saving | Whether DST applies at that instant |
| local_date / local_time | ISO local date/time in the sampled zone; not localized display strings |
| locale | Current host configuration language tag |
| first_weekday | Locale week start: Monday1 through Sunday7 |
| hour_cycle | User display preference: `h12` or `h23` |

Read again after resume or before date-sensitive operations; do not cache timezone
or offset indefinitely. Local times can repeat or disappear across DST changes.
Use epoch time for instants and preserve timezone/date semantics for calendar
entries. Use monotonic time for intervals only within the same boot. The snapshot
is device-reported, not authenticated server time. No HLS/media or calendar APIs
are implied by this clock service.
