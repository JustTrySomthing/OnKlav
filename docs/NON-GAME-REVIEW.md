# Current reconciliation — 0.65.0 source only

Use TODO.md and RESUME-0.65.0.md as the current status. Historical entries below
are checkpoint records and are not a fresh list of missing implementations.
Eight-app document backup is complete; retain the installed speech engine.
Profile sharing was not in the original local-only Profile. Current source adds
Drive authorization/upload/public links and recovery, shared speech Settings,
bounded Files duplicates, provider-first read updates, Browser Find counts and
complete per-profile clear, and YouTube Back/rotation behavior. Existing capture,
viewer, group history/reactions, Browser handoff and Walkie transport/recovery are
implemented. Phone/account/release qualification is still unclosed; no APK built.

## Historical records follow

# Non-game application review — 0.46.0

Charles deferred game remakes until the rest of OnKlav works well. Leave game
sources, rules, art, packages and runtime untouched. Keep the Games tab present.
This is a source/call-path and framework-host review; no phone was available for
new visual, carrier, media/account, or measured responsiveness acceptance.

| Priority | Area | Current behavior and next work |
| --- | --- | --- |
| 1 | Messages and Groups | Provider browsing, conversations, metadata, folders, reminders, group editing and recovery exist. Default SMS/MMS takeover remains disabled (`MessagingTransport.MMS_READY=false`). Complete/verify carrier receive/send, attachments, dual SIM, reboot and provider recovery before treating this as the everyday messenger. |
| 2 | Contacts, app startup and Settings | This pass fixes contact-editor return/permission refresh, truthful loading/error/retry/search states, Apps manager bootstrap cancellation, Storage retry and Walkie settings load recovery. Continue profile, permissions, backups, notifications, Quiet Hours and large-font/Back checks on the phone. |
| 3 | Notes and private data | Real Rust-backed signed SDK app. Verify editing/autosave, rotation/process death, search, export/share, and removal with/without data on the phone. Preserve recovery and prior own-data adoption paths. |
| 4 | Browser, YouTube and RSS | Signed SDK controllers with host network/media components, internal handoff and per-app website isolation/cleanup exist. Verify real sites/feeds, playback, fullscreen/Back, private mode, account policy, rotation and network failure/retry. Finish any remaining reference behavior gaps without inventing services. |
| 5 | Read Aloud and Annotation | Shared speech settings/audio ownership, prior Reader draft adoption, bounded image import/export and drawing restoration exist. Original voice identity/engine parity and phone speech/gesture/provider/process recovery remain open. |
| 6 | Walkie Talkie | SDK offline shell, session/clip state and host audio/transport bridges exist, with live carrier gates closed. Finish and verify phone audio/focus, permissions, connection status/timeouts, notifications/background recovery and actual two-phone use. |
| 7 | Settings services and SDK release readiness | Appearance and local settings work through the host; account/Drive service work and full backup restore/provider acceptance remain incomplete. Review package updates/revocation/removal and external-app broker boundaries. Social stays the requested Hello World placeholder. |
| Later | Games | Explicitly deferred. Existing game content is retained; no new game completion or acceptance is claimed. |

## Concrete corrections in this pass

- `ContactsScreen` now refreshes on lifecycle resume (including returning from
  the Android contact editor or permission settings), clears stale selectable
  contact snapshots on reload/revocation, and distinguishes loading, provider
  failure, no contacts and no matching search results. Null provider cursors
  are unavailable data, not an empty address book. Retry keeps the search text.
- Both contact queries must complete before a snapshot is published. Bounded
  traversal checks coroutine cancellation and propagates normal cancellation.
- `SdkAppsCenter` separates bundled setup from epoch-driven list refresh.
  Its own installs cannot cancel the bootstrap. Loading/error/retry is visible,
  primary install/download/permission/removal controls wait until the catalog loads, and normal cancellation does
  not become a user-facing failure.
- Storage catches failures in the entire initial review, including temporary
  export scanning; offers retry and refreshes on resume. Cancellation propagates.

- Walkie settings now distinguishes loading from failed preference reads, offers
  retry, and propagates normal cancellation instead of displaying it as failure.

Build/signature, host tests, phone runtime and carrier acceptance are distinct.
A green host check cannot close the carrier or same-phone performance work.
