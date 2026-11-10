# 0.49.0 — screenshot corrections and loading/file handoff

User corrected stars: selected stars must be filled and use the active theme,
not a hard-coded yellow. Screenshot references were supplied in this chat;
original Inner Circle executable/source is not present in this checkout.

Production changes:
- Shared ThemeStar uses the reference star path, filled/outline variants and a
  ripple-free touch target. Gallery exposes a dark rounded star control on every
  tile, with direct toggling; viewer uses the same theme control.
- Conversation rows and message headers load Android contact photos off the UI
  thread and use theme-colored initials as fallback.
- Chat loading no longer queries the entire inbox first. Provider rows are
  cached for 30 seconds with observer invalidation and bounded chat retention;
  current private metadata is reapplied. Canonical recipients are cached.
- Initial history positioning snaps rather than animating through all messages.
  Already-read messages are excluded from redundant provider read/seen updates.
  A final UI read-state guard also skips already-acknowledged threads, avoiding
  cache invalidation merely from re-entering a read conversation.
- Files package v8 opens regular files on primary tap; Options retains management.
  Host handoff opens Notes in-process, clears focus, and uses the existing media
  viewer/player for internal images/audio/video. Larger file/folder icons use
  the theme accent. Successful SDK close clears busy, allowing retained screens
  to become interactive again.

Focused Android checks now use one Gradle invocation; test tasks compile their
dependencies automatically, avoiding two repeated daemon/configuration startups.
Runtime-bundle freshness validation and the existing test time bound remain.
APK assembly fingerprints resource/dependency inputs and resets only generated
DEX outputs when they change, preventing stale duplicate R classes while
retaining Kotlin-only incremental builds.

Games and carrier/Drive acceptance are unchanged. This is a debug ARM64 phone
feedback build; it is not an optimized size comparison or measured handset
performance claim. Files->Notes->Back and contact/photo/theme visual acceptance
still require the user's handset. Do not repeat the old unbounded UI-idling
loop as proof of a fix. The three-recipient Family styling fixture is docs/fixtures/group-style-family.json.
GroupsFlowHostTest also contains a Family interaction fixture. Neither adds fake
contacts or groups to production user data.
