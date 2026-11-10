# Calendar UI and everyday feature review

The app remains named **Calendar** in the desktop catalog and workspace. Its native
vector icon is a simple rounded calendar outline, binding rings, header divider and
five date dots; 24-unit viewport and 1.8-unit rounded strokes match the other outline
app icons. It is tinted through the normal OnKlav icon/theme path, not a bitmap.

## Refinements

- Explicit **Add event** primary action and consistent help/empty-state wording.
- Narrow/large-text headers use a compact Today icon and keep Calendar on one line.
- Search takes input focus when opened, avoiding a second tap before typing.
- Week range includes both month names, and both years across New Year.
- Week all-day counts are labeled buttons opening that day. Day view starts at its
  all-day cards when present instead of automatically scrolling past them.
- Multi-day all-day cards show the inclusive date range before opening details.
- Long calendar names have a bounded two-line trailing label, preserving event titles.
- Calendar color choices wrap rather than overflow on small phones; each has a
  48dp target, spoken color/calendar label, selected state and a visible checkmark.
  Removal is actually disabled for calendars with saved events.

## Feature assessment

The requested offline essentials are present: day/week/month/agenda, search,
local named color calendars, timed/all-day/multi-day events, event zones,
daily/weekly/monthly/yearly recurrence, per-date editing/deletion, duplicate,
Undo, up to five independent silent/sounded custom reminders, snooze and
portable .ics import/export. New/edit forms keep Save visible and date/time
controls adapt to narrow phones/large text. Existing event storage and reminder
semantics are preserved; no new dependency, service or account requirement.

Cloud collaboration/invitations, widgets, holiday subscriptions and the Android
system CalendarProvider are separate enhancements, not delivered in this offline
scope. Calendar is not yet in App Documents Backup; export .ics to back up events.
Actual phone notification/sound/Doze behavior and external ICS interoperability
still require device acceptance. This review does not claim store certification.

## Validation

Seven UI checks passed, including the two new all-day/focus/date-range and small-phone
color-picker regressions. Full lint includes production and test source and has zero
errors. The first run exposed the auto-scroll problem; the final recompile and UI run
passed after correction. A large-text header-only refinement was then compiled
successfully without repeating the broad suite. Existing 25 non-UI focused checks
remain the date/storage/ICS/model/reminder/intent baseline. Together there are 32
unique focused Calendar checks; only the seven UI checks were rerun for this review.
See calendar-verification.json for sequence and source hashes.
No new APK was requested.
