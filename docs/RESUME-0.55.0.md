# 0.55.0 — Read Aloud continuation

Kotlin-only correction batch within Group 1 of FINISH-NON-GAME.md.

- Generic editor overflow rejection shows the existing themed error dialog and
  retains the prior text/selection.
- Speech Previous/Next/seek move the unique current matching editor caret, only
  when its canonical text matches and no local edits are unsaved. No focus request.
- Reaching the end through a playback control stays at the end.
- Queued engine startup claims shared speech ownership before it waits. A newer
  reader cancels the old queue. Stop/close/revocation cancel pending playback.
- Retry has a separate initialization generation so old setup callbacks cannot
  mark the replacement engine ready or start obsolete speech.

Production paths: signed Reader Wasm frame -> SdkSpeechTransport ->
SdkSpeechController; caret requests through SdkAppModel -> shared SdkWidget editor.
The Rust controller, guest package, installed Android TTS engine, voice identities,
icons and normal layout are unchanged. No Java application source added.

Evidence: source-verification.json, apk-verification-debug.json,
evidence/v0.55.0. Framework-host results do not establish audible handset output,
physical gestures or carrier acceptance. MMS_READY remains false.

Four developer work groups remain open; Group 1 has Reader progress, not full
app parity. Next inspect Browser/YouTube/RSS/Annotation against current production
paths and select a bounded missing operation. Games remain deferred; Social stays
Hello World. Preserve canonical Library source identity on the next update.
