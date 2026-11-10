# OnKlav 0.42.0 — design, activities and cleanup continuation

Read-only reference: Inner Circle 2.2.85 source v235. New application code is
Kotlin/Compose and Rust; static icon geometry is artwork. No old application
module or Java application source was copied into OnKlav.

## Production changes

- Rust MMS dispatch validates all text parts before creating decoded attachment
  files. Publication uses exclusive file creation and tracks owned paths before
  writes. A partial write/flush failure removes this call's attachments and
  retains unrelated files; a failed rollback is surfaced as needing attention.
  This is error-path rollback, not a claim of crash-atomic cross-provider writes.
- Kotlin records app-removal intent durably before Rust changes the registry.
  If Rust removal commits but private-data deletion fails, the app is shown as
  removed and cleanup stays pending. Rust removal can be retried idempotently;
  install/launch cannot cross an unresolved cleanup. Native profile rotation and
  cleanup phase commit together; retrying native cleanup cannot rotate twice.
  Thumbnail deletion failures are reported and retained for retry. Walkie cleanup
  flags survive asynchronous cleanup and startup failure is surfaced without
  crashing the launcher. Shared speech settings and other app profiles remain.
- Group conversation shortcut: latest-message position offers activities; when
  scrolled away it scrolls to the latest message. Member selection excludes self,
  canonical duplicates and empty numbers. The themed member sheet loads contact
  names/photos, uses original 40dp portraits, formatted numbers, compact labels
  and a height bound relative to the viewport.
- The chooser restores two 64dp activity tiles, Voice chat / Play game, the
  selected person's name and Cancel. Voice routes to the same signed internal
  Walkie app. Games opens a modal contact-bound catalog and returns to the
  original group/chat on Cancel. Opening these surfaces transmits nothing.
- SDK apps now share original 24dp borderless toolbar/navigation artwork with
  48dp targets, including home/history/bookmarks/reload/files/back/forward/go.
  Fields restore neutral rounded surfaces. Feed/Gallery/Starred restore selected
  surfaces and original compact spacing. RSS v2 restores the compact search/
  refresh row and source-first article cards, 14dp corners, headline/read weight,
  three-line previews and a separate bookmark hit target. No app-specific Android
  binary is produced; the same renderer primitives are available to SDK apps.

## Explicitly unfinished

The member/activity picker and routing are implemented. Friend game invitation,
verification/status and play cannot be operational until the original game
ports and their carrier protocol are completed. That separate migration work
was already open and is not replaced by engine exercises or a fake success.
MMS/default-role gates remain closed. Actual dual-SIM/carrier/provider behavior,
two-phone Walkie/audio, handset pixel/frame/large-font/screen-reader acceptance,
website/account playback, bundled speech-engine/voice parity and independent
security review remain unverified or unfinished. This is not full migration
completion. Exact visual parity cannot be certified by host tests alone.

## Verification

Final results and artifact hashes are in verification.json, source-verification.json
and apk-verification.json; evidence/v0.42.0 retains test results and diagnostics.

The broad host run executed all 136 app cases; 134 passed, the new member-sheet
fixture asserted before its asynchronous names/count were ready, and one
existing restore-review case timed out. The Groups test now waits for the real
contact row within its existing timeout. Both classes passed the single focused
follow-up; shared checks passed too. The final count is 148 unique Kotlin cases
across broad/focused runs, not a claim of a fully green broad invocation. The
intermittent recovery timing remains recorded, with no timeout increases, skipped
tests or repeated full-suite attempts. Production classes were byte-compared
with the runtime bundle after focused verification.
