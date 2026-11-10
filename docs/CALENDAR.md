# Calendar — offline OnKlav app

Host-native Kotlin/Compose calendar in the existing single APK. It shares OnKlav themes,
font scaling, back navigation and Files pickers. No account, network request, added binary
dependency, Google service or Android CalendarProvider permission is needed.

## Everyday workflow

- Day timeline; horizontally scrollable seven-day timeline with aligned sticky headings;
  six-week month grid with today/selection/event indicators; upcoming 60-day agenda.
  Timelines open around the first event or 8 AM and divide overlapping appointments into
  separate columns. Search covers saved titles, locations and notes across calendars.
- Full-screen new/edit form with fixed Save and validation feedback; date/time controls stack on narrow screens or at large font sizes. Fields include title, local color calendar, all-day switch,
  start/end dates and times, event zone, location and notes. Multi-day all-day ranges show
  an inclusive Last day and store an exclusive end. A date picker supports jumping dates.
- Daily, selected-weekday weekly, monthly and yearly repetition, custom interval 1–99,
  optional inclusive end date, per-occurrence exceptions. Monthly 29/30/31 and yearly leap
  dates skip invalid dates. Local time is retained over DST. Edit/delete one date or the
  series; duplicate; confirmed deletion and guarded Undo.
- Up to five different reminders: 5/15 minutes, one hour, one/three days, or custom
  minutes/hours/days up to one year (0 = event time). Each independently chooses silent
  notification or alarm-tone notification. All-day offsets count from midnight.
- Named color calendars with visibility toggles, rename, color and empty-calendar removal.
  Configurable week start and default event duration. Clock style follows the phone.
- Calendar .ics import/export through OnKlav Files. Preview counts and choose the destination calendar before adding; duplicate
  UIDs are kept rather than overwritten. Unicode/escaped text, local/UTC/TZID dates,
  all-day ranges, supported RRULE/EXDATE and DISPLAY/AUDIO alarms round trip. COUNT is
  converted to an equivalent final occurrence date. Unsupported rules, modified-instance
  series, custom time zones and unsupported alarms are reported as skipped rather than
  silently flattened. Import is capped at 24 MB/10,000 entries/100,000 counted repeats, so the full supported local event set can be exported and re-imported. Bulk import offers guarded Undo.
- Standard Android INSERT event intents can propose an event in OnKlav Calendar. The
  user still reviews the form and explicitly presses Save. No received intent auto-saves.

## Storage and reminders

Private AtomicFile JSON, 8 MB/10,000 events/32 calendars, validates the complete state
before replacing it. Failed reads do not become an empty replacement. No plaintext events
are exposed in Files until the user exports. Export .ics for a portable backup; Calendar is
not yet part of the existing Rust App Documents Backup bundle. Do not claim automatic
backup or Google cloud synchronization.

One PendingIntent alarm points at the next due reminder across all events. On delivery,
notification tags and a bounded delivered ledger prevent duplicate reminders; then the
next alarm is calculated. Event changes/deletion remove stale notification/snooze targets.
Snooze 10 minutes persists through process death. Boot, package update, time/zone and exact
alarm permission changes recover scheduling. Reminders up to ten minutes late can catch
up. Hidden calendars still remind; visibility only changes browsing.

Silent and alarm-tone notification channels remain separate. Alarm sound uses the system
alarm tone/audio attributes; it is a notification sound, not a looping full-screen alarm.
Notification permission, channel settings, alarm volume, DND, force-stop and OEM battery
policies remain under Android/user control. Missing notification/exact-alarm permission is
visible with an Enable action that opens Android settings directly, including after a permanent denial. Exact scheduling falls back to inexact when permission is
off; reminders may be delayed. No permission bypass or persistent polling service is used.

## Release checks

The APK verifier now requires Calendar’s model, reminder receiver and event-insert activity
to be defined in the executable package. This implementation does not build a new APK.

Full lint initially reported 13 pre-existing findings outside Calendar. Deliberately native
Code Edit input and host-only reflective/test-fixture calls now have narrow documented lint
annotations; Media3 host fixtures explicitly opt in. Power accessibility descriptions have
Spanish and Arabic resources. No Radio, Write or Code Edit runtime behavior was changed.

## Honest scope and qualification

This is an offline personal calendar, not complete Google Calendar service parity. No
cloud sharing, invitation/RSVP server, account sync, Android CalendarProvider integration,
home widget, tasks service or holiday subscription is claimed. Calendar-file interoperability
covers the stated subset; arbitrary VTIMEZONE/BYSETPOS/BYMONTHDAY rules are not supported.
Export local events before deleting app data or uninstalling.

See calendar-verification.json and evidence/calendar for actual host/UI results. Real phone
reminder delivery, lock-screen sound, reboot/Doze/force-stop behavior, external calendar
interop, accessibility and store qualification still need device acceptance. No APK was
requested for this implementation.

Initial implementation validation: 30 unique focused tests passed (15 dates/ICS/storage, 5 UI, 3 reminder delivery, 5 model/transaction, 2 external-intent checks), production/test compilation passed and full lint including test source has zero errors. Final date/time UI was rechecked; notification settings recovery was then compiled. The Apps-center inclusion label is a text-only follow-up. Logs and actual rendered screens are retained. No APK or phone certification is claimed.

## UI follow-up

See CALENDAR-UI-REVIEW.md for clearer all-day navigation/date ranges, focused search,
responsive accessible color choices, compact large-text header and simplified outline
icon. Seven UI checks and full lint passed, followed by compilation of the header-only
layout refinement. No new APK.
