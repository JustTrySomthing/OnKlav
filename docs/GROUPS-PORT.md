# Groups — 0.9.3 development port

Reference: Inner Circle 2.2.85 (version 20285), read-only source archive version
235. Inspected MainActivity groupsPage/showGroupEditor/chooseGroupMembers and
ThreadAdapter, MembersDialog, GroupStore, GroupWire, GroupFeed and GroupContext.
No reference application implementation is included in this project.

## Production implementation

GroupsPane/GroupEditor/GroupPeopleChooser (Kotlin Compose) -> ChatStore ->
NativeCore JNI -> Rust messaging State.groups / group_save / group_remove.
Rust validates recipient identity/count, names, canonical duplicate numbers and
IDs and atomically commits the same private messenger store. A name change
retains the thread; changed recipients resolve a different Android thread, while
old conversation messages and controls remain intact. Saving without provider
access retains a recipient group with thread 0. Opening it later retries thread
binding and migrates its private folder/pin/mute/read controls. No legacy private
data is imported.

GroupsPane -> MessengerPane -> MessageRepository -> Android SMS/MMS provider is
the history path. Saved group thread intents route through the Groups tab and preserve exact
message key/date targeting. Group conversation drafts, attachments, stars, locks, reminders,
folders and notification preferences use the existing Rust messenger state.
GroupProtocol -> NativeCore JNI -> Rust group_wire parses the original versioned
post/reaction envelope. Only unique settled targets receive actor-scoped reaction
associations; ambiguous, missing-parent, invalid and attachment events remain
visible. Outgoing posts/reactions use the group MMS path, never bulk personal SMS.
MMS_READY stays false; no carrier send is represented as working or tested.

| Reference behavior | Implemented in this checkpoint | Remaining acceptance/gaps |
| --- | --- | --- |
| Groups header, original circled plus, compact folder/search/selection controls | Kotlin shared chrome and original vector geometry | Exact handset/large-font/theme comparison |
| Named group rows, initials, recipient count, edit pencil, pin | Kotlin list; Rust saved groups/flags, pinned-first alphabetical ordering | Full swipe animation parity; original unread/selection sRGB surface mixes and selected pin glyph are restored |
| Create/edit name, 2–10 contacts, selected count, Cancel/Done | Compose dialogs and Android Contacts query; Rust validates final save | Contact portrait artwork; per-SIM own-number discovery; large contact books capped at 5000 |
| Membership change starts another conversation | Resolve recipient-set thread, keep original history untouched | Carrier/provider/device verification |
| Members sheet | Bottom sheet, You/phone, resolved names, count including you, close/drag dismissal | Original provider-derived membership refresh, exact layout/large-font acceptance |
| Notifications/read status, pin, folders and selection | Rust private controls, shared conversation notification path, bulk folders/read, swipe left/right | Provider read-state mutation from group list; original complete swipe/action animation |
| Delete group | Explicit local metadata confirmation; messages/other members untouched | Permanent provider-message trash deletion intentionally not claimed |
| Search | Name/recipient and current provider conversation snippet | Full historical message-body search, original paged list loading |
| Group conversation | Shared Feed/Gallery/Starred, search loaded history, drafts/media/message actions/reminders; Rust envelope projection | Original reaction animations/chooser/count presentation, older parent-context query, exact feed/gallery parity |
| Carrier group messages | Existing gated Android MMS + Rust codec; compatible post/reaction envelopes | Actual incoming/outgoing MMS/default-role/dual-SIM/carrier recovery acceptance; MMS remains disabled |
| Friend activities | No fake games/audio transport | Walkie/game member activity picker and their transports |

Only OnKlav is an APK; this is a built-in host module. SDK packages and untrusted
runtime/capability isolation remain separate unfinished platform work.

## Verification boundary

See verification.json, native-provenance.json and evidence/v0.9.3. Rust is rebuilt
for Linux host JNI, arm64-v8a and x86_64 with NDK 27.2.12479018 and 16KB load
alignment. The Godot pack is unchanged; it is byte-checked against the previous
checkpoint and again against the packaged APK. Android framework-host tests use
real Compose, actual Kotlin Group UI and production Rust JNI, with fixture
Contacts rows and no carrier/device messaging. These are not handset, installed
APK or carrier checks. No software emulator boot loop is part of this task.

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
