# SDK implementation chunks

Apps only; games and release packaging remain deferred. Continue through authorized batches without confirmation gates.

Source-complete work still needs separately recorded hardware/provider qualification. Latest evidence: recurrence-reminders-verification.json.

## 1. Accounts and secure networking

Status: source_complete_qualification_pending

| Chunk | Work | Status |
| --- | --- | --- |
| 2 | Signed account/provider policy and install permission review | complete |
| 3 | System-browser PKCE launch and exact callback/state validation | source_complete |
| 4 | Host-only encrypted account/token handles | source_complete |
| 5 | Token refresh, sign-out and removal cleanup | source_complete |
| 6 | Origin-bound authenticated HTTPS requests | source_complete |

## 2. Background jobs, downloads and media

Status: source_complete_qualification_pending

| Chunk | Work | Status |
| --- | --- | --- |
| 7 | Durable job records, ownership and status/cancel API | source_complete |
| 8 | Android job execution, constraints and reboot recovery | source_complete |
| 9 | Bounded binary download to private staging | source_complete |
| 10 | Validated resume, integrity checking and atomic completion | source_complete |
| 11 | Shared local-file player and progress/seek component | source_complete |
| 12 | HTTPS streams, queue and playback-speed controls | source_complete |
| 13 | Background media session, audio focus and lock-screen controls | source_complete |

## 3. Calendars and reminders

Status: in_progress

| Chunk | Work | Status |
| --- | --- | --- |
| 14 | Trusted clock/timezone API | source_complete |
| 15 | All-day ICS import/export | source_complete |
| 16 | Themed agenda and event editor | source_complete |
| 17 | Bounded recurrence with timezone/DST handling | source_complete |
| 18 | Reminder pending/status and absolute-date scheduling | source_complete |
| 19 | Snooze/repeat actions and missed-event policy | source implemented; integration/device pending |
| 20 | User-selected Android calendar integration | source implemented; integration/device pending |

## 4. Device services and app sharing

Status: pending

| Chunk | Work | Status |
| --- | --- | --- |
| 21 | Connectivity status and Android settings handoff | source implemented; integration/device pending |
| 22 | Scoped foreground location with manual fallback | source implemented; integration/device pending |
| 23 | Torch ownership, timeout and cleanup | source implemented; integration/device pending |
| 24 | Selected BLE scan/connect/read/write/notify | source implemented; integration/device pending |
| 25 | Separately scoped nearby Wi-Fi and LAN workflows | source implemented; integration/device pending |
| 26 | Typed file/share handoff and handler discovery | source implemented; integration/device pending |
| 27 | Cross-app/publisher ownership and removal checks | source implemented; integration/device pending |

## 5. Reading, maps, adapters and examples

Status: pending

| Chunk | Work | Status |
| --- | --- | --- |
| 1 | Bounded literal text search with Unicode offsets | complete |
| 28 | Shared text search UI and saved reading position | source complete; Android qualification pending |
| 29 | EPUB chapters/TOC, reflow and search | text profile source complete; Android qualification pending |
| 30 | PDF pages/search/selection and shared viewer integration | source implemented; Android renderer/IPC/device pending |
| 31 | Map renderer with provider configuration and attribution | private raster source implemented; full-host/device pending |
| 32 | Bounded tile cache and offline-data policy | source implemented; transport/provider/device pending |
| 33 | Geocoding and routing adapters | configured adapter source implemented; live-provider/device pending |
| 34 | Podcast enclosures and radio metadata adapters | bounded RSS2/Icecast source implemented; qualification pending |
| 35 | Email transport/account and bounded mailbox sync | configured JMAP header profile source implemented; qualification pending |
| 36 | Safe MIME, attachments, drafts and confirmed-send UI | bounded JMAP profile source implemented; documented format/upload gaps and qualification pending |
| 37 | Weather adapter, units, attribution and stale-cache UI | current model profile source implemented; qualification pending |
| 38 | Public-API reference apps for the new services | initial reference source set implemented; workflow/qualification gaps documented |

## 6. Final qualification

Status: pending

| Chunk | Work | Status |
| --- | --- | --- |
| 39 | Phone media, TalkBack, size/IME and process-recovery qualification | requires_environment |
| 40 | Two-phone delivery/receipts/reconnect/removal qualification | requires_environment |
| 41 | Bounded sustained performance/heap profiling and security review | limited host checkpoint; Android/heap/adversarial gates pending |
| 42 | SDK/package/documentation and APK release preparation when requested | deferred_release |

Next plan chunk39 requires physical-device media/accessibility/process qualification. Chunk40 requires two phones;41 remains bounded performance/security review.
