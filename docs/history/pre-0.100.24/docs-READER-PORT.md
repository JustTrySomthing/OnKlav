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

# Backup continuation — 0.61.0

Settings Backups now covers the single current saved Read Aloud draft.
See SETTINGS-PORT.md and RESUME-0.61.0.md for merge limits and evidence.
Existing data wins; full SDK-data backup remains unfinished.

# Read Aloud SDK migration — 0.12.0 development checkpoint

Read Aloud is an actual independently compiled Rust/Wasm `.onklav-a` app. Its controller, draft edits, Paste/Clear actions and settings/dialog composition live in `sdk/apps/reader`. Generic Kotlin/Compose widgets render its validated frame. The host speech component is reusable by other signed SDK apps and requires the explicit `speech.output` capability. The only APK is OnKlav.

## Implemented and verified

- Original screen structure: header count and voice-settings gear, adjacent Paste/Clear controls, 50,000 UTF-16 editor, position/excerpt, seek and Previous/Play/Pause/Next. Original private draft storage is recreated with the SDK Rust atomic record service.
- Paste replaces a selected UTF-16 range, respects surrogate boundaries and retains the current text when the result exceeds 50,000 units. Clear has Cancel/Clear confirmation. Editor over-limit changes are rejected with a visible notice; the existing draft and selection are retained.
- Speech is a generic host-owned component, not an Android API object passed to guest code. Live Rust gates bind access to the installed package, current session, current speech widget and revocable grant. Private application IDs scope speech voice/rate preferences; Reader and messenger share the Reader preferences and one-current-reader ownership.
- Offline phone voices only, bounded 900-unit chunks, range callbacks, stale-callback generation checks, pause/resume, previous/next offsets, seek, audio-focus loss stop, background stop and disposal shutdown. Rate follows the original 0.75–1.50 range. Preview shares that app screen’s engine and restores the document position when settings closes.
- Android 11+ TTS service visibility query is present and asserted in the compiled APK manifest.

## Evidence

39 Rust and 28 Kotlin tests pass, including the actual signed Read Aloud Wasm package. Rust exercises private draft reopen, full-length Unicode, selected paste, overflow preservation, clear confirmation and live speech grant revocation. Robolectric runs the real Compose screen/JNI and tests editor/clear/settings/reopen through production accessibility actions; it does not simulate physical touch. A separate fake audio backend tests chunk/range progression, pause, seek, preview restoration, revocation and ignored late callbacks. Actual audible output requires a handset with offline voices.

The passing Android test phase took 112.8s and APK phase 50.8s, each bounded at 180s. After adding the TTS manifest query, APK packaging and verification were repeated without changes to Kotlin/Rust behavior. Signature/CRC/16KB alignment, both ABI Rust bytes, and exact Notes/RSS/ReadAloud package and Wasm hashes pass.

## Remaining parity and data gaps

This uses the phone’s installed offline voices. Original bundled male/female voice identities, included engine/model assets and speech license/setup panel are not ported. Keep the current installed offline engine; changing engines/assets is outside this correction batch. Shared messenger voice preferences and one-current-reader ownership are implemented. Voice availability and quality are not established by host tests. The original 2.2.85 screen is the reference, not the previous partial Kotlin 100,000-character file-import tool; that transitional source remains available but normal desktop launch uses this SDK package.

The prior rewritten Kotlin Reader draft is adopted once into an empty never-updated SDK draft, with durable-save verification before marking adoption. The original preferences are retained. Voice/rate preferences are shared with messenger. No Inner Circle private files are copied or deleted. Voice settings retention on SDK delete-data, exact popup typography, disabled/control styling, large-font/accessibility/physical gesture acceptance remain open. Text-only draft is within the original ReaderActivity scope; do not invent document-import features as a substitute.

## Build/test repairs

The initial UI test pressed Back while voice settings was closing; it now waits for the dialog to disappear. The final clear assertion originally counted the hint as text; it now checks the actual EditableText value. A real stale-frame issue was fixed: closing an SDK session clears its frame before reopening so speech does not initialize against the closed session. All regression tests pass with those fixes. Debug R8/generated-cache repairs from 0.11.0 remain in effect.

## 0.55.0 — bounded Reader continuation

The generic host editor reports rejected over-limit input using the existing dialog.
Speech Previous/Next/seek issue a caret request only to the unique matching editor
in the current frame, with exact text and no unsaved changes. They do not request
focus. Seeking to the end does not resume at the beginning.

Queued initialization participates in shared speech ownership: a newer reader,
Stop, close or capability revocation cancels queued speech. Retry invalidates the
old initialization callback independently of normal playback callbacks.
The installed engine, voices, screen layout and icons are retained.
Focused evidence is recorded in source-verification.json; audible phone output
and full original visual/accessibility acceptance remain separate.
