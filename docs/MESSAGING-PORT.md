# Messenger rewrite checkpoint — 0.9.2 development

This is an authored Kotlin/Compose + Rust port against Inner Circle 2.2.85.
No original Java implementation, original DEX, APK or messaging library is
embedded. Original emoji names/catalog and vector artwork are reference data.
Only the OnKlav host is installable; messenger is a host screen, not another APK.

## Production call paths

| Operation | Production path | Evidence boundary |
| --- | --- | --- |
| Desktop drag/page turns | AppsDesktop -> anchored drag ghost, indication-free cell, LazyGrid placement spring, Compose pager tween; LauncherLayout persisted gaps | Device drag/page/recreation regression; physical frame timing still required |
| Conversation discovery/history | MessengerPane -> MessageRepository -> Android SMS/MMS provider + Contacts; content observer refresh | Requires READ_SMS; OEM provider, large-history and carrier acceptance remain |
| Drafts/folders/pin/mute/read overrides/flags | Compose -> ChatStore -> NativeCore JNI -> Rust messaging::dispatch -> fsynced atomic messenger.json | Rust persistence/rejection/outcome tests and Android JNI/UI regression |
| SMS sending | Composer -> repository -> MessagingTransport -> Rust stage -> SmsManager -> explicit per-part callbacks -> Rust outcome -> provider update when default | Real SIM/carrier and delivery-report acceptance required; never automatically resends |
| MMS wire format | Kotlin codec bridge -> Rust mms::dispatch compose/parse/ack -> private UUID PDU/media files | Bounded fixture, Unicode/attachment roundtrip, truncation/path rejection tests; not carrier acceptance |
| MMS carrier transport | SmsManager sendMultimediaMessage/downloadMultimediaMessage -> private grant-only provider -> result receiver -> Rust parse and Android provider persistence | Implemented development path, still gated OFF pending incoming/outgoing verification |
| Incoming SMS | Protected SMS_DELIVER -> serialized executor -> durable raw recovery copy -> deduplication -> Android inbox -> notification | Default-role/device and real carrier acceptance pending |
| Notifications/reminders | Kotlin notification channels, explicit mark-read/reply intents, Rust flags, AlarmManager, boot restore | Notification permission/channel/Doze and handset tests remain |
| Media | SAF -> bounded decode/size/MIME validation -> private blob; Android MMS part reader -> grant-only open/share | Gallery/forwarding structure implemented; physical camera, recording, detailed media parity remain |
| Setup | Live permissions/role state, contacts/notification access, active SIM chooser, own-number preference, app settings, pending MMS retries | Default takeover deliberately unavailable; no real subscription tested |

## Current UI behavior

Inbox/Archive/Trash, search of names/numbers/snippets, selection and batch moves,
conversation pin/mute/read actions; chat Feed/Gallery/Starred; earlier-history
loading and a bounded provider-history search dialog with jump-to-result; compact reference-style composer,
emoji search, private attachment drafts, actual SMS attempt, status and errors;
message star/lock/copy/forward/remind/open/share/delete paths. Destructive provider
writes require default status. Local folders do not delete phone messages.
Reactions send readable SMS text; incoming proprietary reaction matching has not
been ported, so received reaction texts are retained as messages.

Quiet Hours and notification preferences now connect to OnKlav's notification
service; reminders can be reviewed, rescheduled and removed. The message menu
now offers hour/tonight/tomorrow presets and a custom date/time chooser. Other messenger settings still
marked unavailable remain unfinished. A non-default host cannot suppress the
current default messenger's alerts or change its read/deletion behavior.

## Acceptance still open

**Not a complete replacement messenger yet.** MMS_READY remains false and Setup
never requests default role while this is true. Existing default messaging is
unchanged. Real sending costs and carrier results must not be simulated as
success. No actual external message is sent by the automated tests.

Must verify SMS provider receiving and raw recovery, multipart delivery and
failure behavior, dual SIM, carrier MMS send/retrieve/ack/provider assembly,
mobile data and APN failures, group own-number identification, notification
permissions/actions and reboot/Doze. Rust MMS currently accepts UTF-8, Latin-1,
ASCII and UTF-16; unsupported transfer encodings/charsets fail with retained raw
files, not fabricated text. Broader carrier codecs need independent fixtures.

Original automatic spam screening, blocked-message review, incoming reaction
association/removal, complete unread/large-history
search, camera/video/voice capture, internal zoomable viewer and exact visual/
accessibility parity remain. Groups/social/walkie transport, SDK and original
Godot game ports remain separate unfinished work. No performance improvement
is inferred from language choice or software-emulator captures.

## Wire/API references

Read as specifications, not copied production implementation:
- https://developer.android.com/reference/android/provider/Telephony
- https://developer.android.com/reference/android/telephony/SmsManager
- AOSP frameworks/opt/mms PduComposer, PduContentTypes and PduPart field registry
  (android.googlesource.com/platform/frameworks/opt/mms).

Exact build, signature, test and runtime outcomes are recorded in
verification.json, apk-verification.json and evidence/v0.9.2. Historical passes
from earlier APKs are not re-labelled as tests of this APK.

## Historical 0.9.0 verification iteration record

The first desktop attempt (APK d96dce6b...) successfully moved Notes into the
last row, then timed out waiting for a new page at the edge. Page animation now
waits for the added page's layout, and its committed job survives leaving the
gutter. The updated desktop regression passed in 138.634 seconds on APK
580c0c7e..., including empty-cell placement, held-edge creation, drop, restart,
dots and original shell/package-manager controls. This desktop implementation
is unchanged in the final delivery; only messenger/provider/label changes
followed that pass. The test's wall-clock wait was also increased for software
emulation; no physical frame-time result is implied.

That run's messenger JNI codec and storage assertions passed, but the UI test
selector matched both the phone dialog and the inbox search behind it. It was
corrected to target the phone field explicitly. The final check adds canonical
combined-provider discovery, actual emulator SMS history, star/draft/folder
operations and Setup/About. See the final runtime result for its outcome.

Own OnKlav 0.8.4 conversation folders are migrated into Rust; no private Inner
Circle preferences/data are read. Draft identities normalize formatting, the
US country prefix and recipient order. Three JVM tests verify those cases.
MMS/role are still gated; passing codec or UI tests does not verify a carrier.

The final b256996d... APK installs with its exact verified checksum and passes
MessengerDeviceTest in 142.909 seconds. The emulator SMS fixture appears in the
real provider and can be starred; private draft reopen, folders, pin/mute, emoji,
Setup and About checks pass. The combined-provider assertion excludes silent
SMS-only fallback on this device. Android JNI MMS codec/private file assertions
pass. There are no crash-buffer entries in this bounded run. No external SMS
is sent, no default SMS role is selected, and no carrier MMS is certified.

## 0.9.1 reminder continuation

See RESUME-0.9.1.md for the restored environment, reference operation, queued-alarm
race correction and current test boundaries. The unchanged Rust mobile binaries
and Godot pack retain the previous verified bytes; Kotlin host code is rebuilt.

## 0.9.2 verified continuation

Robolectric/API29 host UI checks now pass the reminder chooser, actual native
date/time widgets, real Rust state, stale-alarm guard and manager actions.
Open Message and reminder notifications preserve key/date identity and target
the loaded message; older history uses Load Earlier Messages. Direct thread
lookup removes the newest-1000-thread scan limit. See RESUME-0.9.2.md. Physical
APK installation and carrier acceptance remain separate and incomplete.

## 0.41.0 — four-area continuation

Implemented durable Messenger callback/provider recovery, older group reaction
parent context and bounded history search, durable Walkie result/background
recovery and late-clip fallback, internal Browser handoff, paged Notes search,
shared speech settings/ownership, own Reader draft adoption, Annotation viewport
restoration and per-app website-profile isolation/cleanup.

Verified 145 Kotlin/framework-host and 144 freshly rebuilt Rust tests
(289 total), with one host APK (code48). This does not complete carrier,
two-phone audio or exact reference acceptance. MMS/default SMS role remain
disabled. See RESUME-0.41.0.md for production paths and explicit remaining work.

## 0.42.0 — design, activity selection and cleanup

- [x] MMS validation precedes decoded-attachment publication; partial writes roll
      back only owned files, and cleanup failures are surfaced.
- [x] Durable app-removal intent, idempotent core cleanup retry, one-time native
      profile rotation, retryable thumbnail cleanup and guarded startup.
- [x] Individual group member selection, original activity tiles, internal Walkie
      handoff, modal game catalog and return to the same conversation.
- [x] Shared original SDK toolbar/navigation glyphs and rounded fields, selected
      conversation tabs, RSS v2 source-first cards and compact search/refresh row.
- [ ] Friend-game invitation/verification/status and play await original game
      ports/protocol; the picker alone does not close that work.
- [ ] Handset pixel/frame/large-font/accessibility, live carrier/dual-SIM/provider
      and two-phone voice acceptance; original bundled speech-engine/voice parity.
- [ ] Independent security/lifecycle review and the remaining wider migration.

148 Kotlin/framework-host and 145 Rust checks passed (293 total). One verified
host APK, code49. MMS/default-role remain disabled. See RESUME-0.42.0.md.

Current 0.50 controls and shared Reader speech behavior are described in RESUME-0.50.0.md. Supplied original-app screenshots remain the visual reference; handset parity is not inferred from host tests.
