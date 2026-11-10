# Calendar components (working source API)

Use `onklav_ui::calendar::{import_events, export_events, validate_event}` for
bounded UTC and all-day calendar data. The existing `import_utc` / `export_utc`
functions retain their UTC-only contract.

An event has string `uid`, `start`, `end`, `summary` and optional `description`.
Timed values use `YYYYMMDDTHHMMSSZ`. All-day events add `all_day: true` and use
Gregorian `YYYYMMDD` dates. The end is always exclusive: a one-day event on
October25 starts `20261025` and ends `20261026`. Export requires an explicit UTC
DTSTAMP. Keep stable unique UIDs when editing or importing.

Imports validate the complete calendar before returning any events. Duplicate
UIDs/properties, mixed DATE/date-time boundaries, invalid/leap dates, local times,
unknown parameters, recurrence and nested components fail visibly. Nothing is
silently stripped. Maximum200 events /256KiB input or output; titles1024 bytes,
descriptions8192 bytes. Text escaping and75-octet UTF-8 folding are built in.
Importing is pure; persist only after the user approves the complete result.

## Shared agenda and editor

- `calendar_ui::agenda(title, events, offset)` returns a chronological,20-row
  agenda using host labels, records and toolbar actions. It accepts at most200
  validated events. All-day ranges display the inclusive last occupied date;
  timed events are explicitly shown in UTC. No local timezone is guessed.
- Handle `calendar.open.N` against the same event snapshot: N is the **original
  input index**, not the sorted position. Resolve its stable UID before editing.
  Page actions are `calendar.next` / `calendar.previous` (offset increments20).
  `calendar.new`, `calendar.refresh` and `calendar.back` belong to the parent.
- `calendar_ui::event_editor(title, input, initial_event)` handles the draft,
  validation, all-day switch, time fields and centered discard confirmation.
  Supply a valid seed event with a unique UID for New. Keep the initial event
  stable and pass the returned editor state into the next call.
- On Save, `frame.state.outcome` is `{kind:"save", event:...}`. Commit that
  event through the app's chosen storage/collection transaction. Close the
  editor only after success. On failure keep the editor state and show the
  service error; never silently retry a stale revision. Cancel emits
  `{kind:"cancel"}` after discard confirmation when needed.

The components perform no storage, network, system-calendar or notification
operation themselves. That lets apps reuse them with private collections or a
future calendar-provider adapter without accidental writes. The complete signed
component/broker example is
[calendar_ui_app.rs](../tests/fixtures/calendar_ui_app.rs); it demonstrates an
explicit revision-checked save and draft retention on failure. This is a test
fixture, not a bundled calendar application.

All UI uses the existing host theme, appearance settings, accessible controls,
scrolling and reserved chrome. No colors, fixed widths or font sizes are embedded.
Date fields use visible YYYY-MM-DD guidance; timed fields use explicit UTC and
preserve seconds. These components do not implement recurrence, reminders,
named-timezone editing or Android calendar access; those remain separate tasks.
