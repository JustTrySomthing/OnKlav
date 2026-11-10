# Working UTC calendar export

`onklav_ui::calendar::export_utc(events, stamp)` produces a small ICS string
for explicit, nonrepeating UTC events. Use the existing user-tapped
`services::export("events.ics", text, callback)` to export it; that step needs
the usual `files.export` capability. The pure exporter itself needs no grant.

Each JSON object requires `uid`, `start`, `end` and `summary`; `description`
is optional. The caller supplies a UTC creation stamp. All timestamps use
`YYYYMMDDTHHMMSSZ`; the end is exclusive. No system clock access is implied.
UIDs must be unique in the export. Invalid dates, zero-length/reversed intervals,
unsupported fields and recurrence/timezone attributes fail before output is
returned. There is no silent conversion or dropping of recurrent events.

The exporter supports up to 200 events and 256 KiB output. Text escaping and
UTF-8-safe line folding follow [RFC5545](https://www.rfc-editor.org/rfc/rfc5545.html).
Leap seconds are outside this limited subset and are rejected. Limits: UID
128 bytes, summary 1024 bytes, description 8192 bytes.

`calendar::import_utc(text)` reads the same UTC-only subset and returns event objects.
It unfolds lines and decodes escapes, rejecting duplicate/unsupported properties,
invalid dates and incomplete/nested containers. It performs no writes. Limits:
256 KiB input, 200 events, 2000 unfolded lines and 20,000 bytes per line.

This is not a full calendar service. Broader import, named timezones,
DST/recurrence, all-day dates, Android calendars, scheduling, agenda widgets and
account synchronization remain unfinished. Do not strip those properties from
an existing calendar to force it through this helper.
