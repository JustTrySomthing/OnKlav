# 0.65.0 — non-game reconciliation and concrete source completion pass

Source only, code73. Last APK 0.61.0/code69. Do not make APKs unless requested.
No game/SDK controller/package/publisher/signing changes. Core Files implementation
changed, so host Rust/JNI was rebuilt; mobile JNI remains deferred.

## Production changes

- content::unique reserves suffix space within 180 UTF-16 units, preserving
  scalar boundaries and bounded extensions. Copy/import/restore and backup merge
  no longer publish names that make the next Files index load fail. New test uses
  original and imported bytes, maximum-length ASCII/emoji and long extensions;
  existing backup rejection test now verifies successful bounded-name restoration.
- ReadAloudSettingsDialog uses SdkSpeechController(host.messages), the same
  preferences, owner, focus, retry and generation guards as messages/Reader.
  Returning from phone Speech Settings refreshes offline voice availability.
  Saved voice/rate choices remain; absent/nonfinite rate defaults to 1.05.
- DriveSharing: Google Identity AuthorizationClient with drive.file only; optional
  account selection and revoke. Transient tokens; provider private-save fallback
  retained. Explicit upload confirmation explains anyone-with-link access and
  account identity. Streams a selected <=100 MiB file into owned staging then a
  bounded, cancellable resumable upload; no redirect/token forwarding or automatic
  retry. Preallocated file ID is persisted before remote creation; an interrupted
  or unknown outcome is retained for explicit account-bound removal. Permission
  publishes read-only, unlisted link only after upload; composer receives the link
  as editable draft text, never an automatic send. Completion links may remain
  remote if app process dies before local acknowledgement; recovery is explicit,
  not a claim of a remote transaction or guaranteed cancellation/deletion.
- Combined App Documents Backup is accessible from Google Drive & Backups too.
- MessageRepository.markRead updates both provider tables with live role/READ_SMS
  checks before local metadata. Non-default local overlays remain supported.
  Multi-table writes are not transactional; failed provider operations retain the
  previous local state for retry.
- Generic web pool exposes Find counts; callbacks bind view/navigation/command and
  live permission. Browser/YouTube clear uses WebStorageCompat on the authenticated
  profile, including cache/cookies/site storage/service workers, with an explicit
  unsupported-provider error and existing late-callback/reload guards. No global
  cache clear. WebKit 1.13.0 supplies the scoped deletion API.
- MainActivity handles orientation/screenSize/keyboardHidden changes to retain
  host app state. Provider web surfaces request fullscreen only for currently
  playing video in landscape, dismiss in portrait, and use native page history
  on system Back after overlay/fullscreen handling. Handset video behavior remains
  unverified; website fullscreen requests may be refused normally by the provider.
- SDK guide reflects existing brokers. Tests-only checks no longer require a
  private Android signing key; no key was generated or APK assembled.

## Reconciliation

TODO.md is concise/current; the complete old ledger is preserved in
MIGRATION-HISTORY-THROUGH-0.64.0.md. Removed phantom profile-sharing and new speech
engine tasks after reading the original local-profile API and current user engine
decision. Existing YouTube isolation is the original private-viewer behavior.
Do not claim every historical unchecked feature is missing, nor call the entire
port complete. External account/phone/release qualification remains in TODO.

## Verification

Final counts/evidence are in source-verification.json. One full Rust run after
correcting the old duplicate-name test passed 186 tests and rebuilt host JNI.
The initial run correctly exposed that old test's now-obsolete rejection
expectation; production bounds were not relaxed to pass it. One focused Android
set covers Drive upload order/bytes/recovery/account/URL policy, speech, provider
read/notification/removal recovery, existing transport recovery, web policy and
combined backup reachability. The scoped WebView library/API and Back changes
receive only their affected web/recovery follow-up checks. No endless emulator,
full UI repetition, live message/upload or carrier/account acceptance.

Google Cloud must register the actual OnKlav application ID and signing SHA-1
for OAuth, enable Drive API and configure consent. There is no service credential
or account in source, and no Cloud project was available for setup. Provider-based
private Drive saves remain independent of this optional direct integration.

Primary API references:
https://developer.android.com/identity/authorization
https://developers.google.com/workspace/drive/api/guides/manage-uploads
https://developers.google.com/workspace/drive/api/guides/manage-sharing
https://developer.android.com/reference/androidx/webkit/WebStorageCompat

Final evidence: 186 Rust, 29 selected app Android and 12 shared Android checks pass (227 distinct). Initial focused Android invocation: 86.7 seconds. Final affected web/recovery follow-up: 76.6 seconds. Final Kotlin runtime JAR matches compiled classes. No repeated full UI suite or emulator. Actual account, clear-data provider and video/rotation acceptance remain unverified.
