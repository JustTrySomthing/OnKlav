# 0.56.0 — Browser and shared web lifecycle

Kotlin-only Group 1 continuation. Production paths: SdkAppScreen's Android Back
-> SdkWebPool active callback -> SdkWebPage -> live authorized native Browser
history; shared SdkWebComponent receives renderer/page/reset callbacks and owns
view retirement/recreation. Signed Wasm and Rust data services are unchanged.

Browser Back uses history, then exits; header Back still exits. YouTube Back
retains reference exit behavior outside fullscreen. Drawers and dialogs retain
priority. Retired renderer callbacks cannot change the active view UI or open
late downloads/fullscreen. A renderer crash clears native tab memory and Reload
creates a new isolated view. Native history lost with the renderer is not promised
to survive. Normal tab persistence/bookmarks/settings are retained.

Clear/reset cookies complete only for the current view, command, navigation
generation and live grant; the newly loaded page clears history on completion.
No global profile/cache deletion, new permission, artwork, icon or layout change.
The first compile caught an old direct private view setter in the renderer callback;
it was replaced with the shared retirement method before final checks.

Evidence: source-verification.json, apk-verification-debug.json and
evidence/v0.56.0. Host provider lacks MULTI_PROFILE; controlled WebViews test
history/lifecycle races, real signed screens test safe fallback/metadata UI.
No physical phone, live website/YouTube/profile or carrier acceptance is claimed.
MMS_READY remains false. SDK/mobile Rust/Godot reuse requires matching proofs.

Next: RSS own rewritten source preferences/scroll retention; inspect current code
before implementing. Annotation recovery follows. Four broader developer groups
remain open; games deferred, Social Hello World.
