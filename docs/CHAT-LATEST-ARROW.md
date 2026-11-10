# Clear jump-to-latest control — 2026-11-01

The regular and group message feeds share MessengerPane and now use one
ChatFeedAction. The jump button uses a rounded, bold 2.8-unit vector arrow at
32 dp instead of a font-dependent thin text glyph. The circle is more strongly
theme-tinted (25% white blend rather than 65%); black/white ink is selected for
contrast. The 48 dp touch target and shadow remain.

The shared callback is unchanged: jump/reset history to the newest message when
away from the end; open the existing peer/group Activities flow when already at
the end. Feed/Gallery/Starred visibility and activity-peer selection are retained.
No message data, recipient routing or send/receive behavior is modified.

Verification is recorded in chat-latest-arrow-verification.json. New checks cover
contrast across all 15 light/dark palettes, the vector control's native rendering,
32 dp icon width, callback taps, and its switch back to Activities. The previous
14 Phone/header/keypad checks are retained, not repeated. Phone now includes the
completed matching-header, clear-keypad, detail-dialog and quick-call refinements.

This change is packaged in OnKlav 0.100.36-rc1/code145; APK verification and
release evidence are recorded in RELEASE-0.100.36.md and apk36-verification.json.
User-supplied screenshots/personal message content are not included in evidence.
