# Recurrence and absolute reminders (working source APIs)

`recurrence::Rule` runs bounded, read-only timezone arithmetic through
`calendar.expand`. It requires no calendar or location permission. For example:

```rust
Rule::timed("2026-03-07T09:00:00", "America/Chicago", "daily", 3600, 10)
    .dst("skip", "earlier")
    .between(window_start_ms, window_end_ms, "expanded")
```

Frequencies: daily, weekly (same weekday), monthly (same day number), yearly
(same month/day). Interval1–365; count1–1000 valid occurrences; result limit1–200.
The caller supplies a half-open epoch window for occurrence **start** times.
Expansion scans at most10000 candidates; inspect `truncated` and narrow/split the
window or request before treating a result as complete. This is a bounded rule
API, not an arbitrary RRULE parser. BYDAY/BYSETPOS/EXDATE and ICS RRULE import
remain unsupported; calendar import still rejects unsupported rules atomically.

Timed starts use local `YYYY-MM-DDTHH:MM:SS` in a named Android zone and an
elapsed duration1–604800 seconds. Gap policy is `skip` or `forward`; overlap
policy is `earlier` or `later`. Invalid month/leap dates are skipped and do not
consume count. Forward shifts nonexistent timed values by the timezone gap.
Dates before1970 or beyond9999 are unsupported. Timezone rules come from the
Android device; no embedded stale offset table or network service is used.

`Rule::all_day("2026-03-08", zone, frequency, duration_days, count)` uses civil
date boundaries, not fixed24-hour days. Day length can change at DST. Midnight
transitions use the first valid instant of the date; entirely missing dates are
skipped. Output contains timezone, occurrences and truncated; each occurrence
has start_ms/end_ms, local_start/local_end and both UTC offsets. Persist the
original local rule as well as any computed instants; re-expand after timezone
rule changes. Expansion performs no scheduling or storage operation.

## Absolute reminders and queries

`notifications::schedule_at(key, title, text, at_ms, callback)` requires the
revocable `notifications.post` grant, a direct tap and Android notification
access. at_ms is Unix milliseconds, at least1 second and at most366 days ahead.
The existing relative60-second–30-day `schedule` API remains compatible. Both
use the same durable bounded host queue (8/app,32 total); a key replaces its
previous entry. Cancel removes queued and visible notifications for that app.

Scheduling replies `{scheduled:true, due_ms, exact:false}`. Android delivery is
**inexact** and may be delayed by battery restrictions; no exact-alarm access is
requested. There is no delivery guarantee or delivery receipt. Absolute latency
is repaired on app bootstrap, reboot, package replacement and clock changes.
Delivery still checks live app/publisher/grants and never runs guest code.

`notifications::status(key, callback)` is read-only and requires the grant but
no direct tap or Android permission prompt. It returns pending, nullable due_ms,
overdue, registered, exact:false and notifications_enabled. Pending means a
queued durable record; registered checks the matching Android job token.
`pending:false` means no queued record, including after cancellation/consumption;
it must not be interpreted as successful delivery.

`notifications::pending(callback)` returns at most8 of the signed app owner's
keys/times/overdue/registered fields, sorted by due time. Titles, message content,
publisher IDs and host tokens are excluded. It also returns exact:false and
notifications_enabled. Queries do not repair jobs or modify queue contents.

Snooze/repeating notification actions, an explicit missed-reminder policy and
user-selected Android calendar integration remain separate work. These APIs do
not turn the calendar editor into a system-calendar app or add RRULE import.

Implementation references: [RFC5545](https://www.rfc-editor.org/rfc/rfc5545.html)
and Android [ZoneRules](https://developer.android.com/reference/java/time/zone/ZoneRules).
