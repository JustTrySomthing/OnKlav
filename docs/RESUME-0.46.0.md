# OnKlav 0.46.0 — non-game application pass

Charles deferred game remakes until the rest of OnKlav is working well. Game
sources/assets/runtime, native core and signed SDK programs remain unchanged.
See NON-GAME-REVIEW.md for the prioritized review of Messages/Groups, Contacts,
Settings, Notes, Browser, YouTube, RSS, Reader, Annotation, Walkie and SDK readiness.

This pass fixes actual user-facing lifecycle/recovery behavior:
- Contacts refreshes after the contact editor/settings return, clears stale
  contacts on reload/revocation, distinguishes loading/unavailable/empty/search
  states and offers retry. Both provider queries must finish before publication;
  bounded traversal respects cancellation.
- The Apps manager separates bootstrap from its own catalogue epoch updates,
  shows loading/error/retry, waits for the catalogue before its primary package controls
  and propagates cancellation. Cancel remains available during catalogue failure.
- Storage catches the complete load (including temporary-export scanning),
  offers retry and refreshes when returning from Android settings.
- Walkie settings distinguishes preference-loading failure from ongoing work,
  offers retry and propagates normal cancellation.

Verification: 23 app host tests across five selected suites passed, zero
failures/errors/skips. New checks exercise denied Walkie private storage -> restoration -> Retry,
unavailable contacts -> Retry ->
selection/search, contact edit -> resume refresh, and permission revocation ->
hiding previously displayed contacts. Existing app installation/removal/permissions,
settings save/cancel, bootstrap and UI workflows also pass. The 12 unchanged
shared tests were UP-TO-DATE; the prior20 Python checks, Rust and Godot tests were
not rerun or counted fresh. Current compiled Kotlin bytes match the runtime jar.
The initial selected invocation had one existing Walkie settings readiness
timeout (21/22 passed); its trace is retained in initial-selected-tests. A
focused Settings suite followed the preference-load recovery correction. No
full-suite retry loop was used. The initial timeout cause was not established.

Optimized ARM64 APK:0.46.0-dev/code53, 38,255,637 bytes.
SHA-256:`ba9f589f43e72a3dbc7da8e962d20eabcae6974c28f8ae9176a65f86ebe464e9`.
Existing package/signer, signature, archive/ELF16KB alignment, required classes,
JNI, internal SDK/Godot contents and non-debuggable/profileable checks pass.

No handset was attached: visual/media/account/carrier/audio acceptance and actual
phone speed remain unmeasured. MMS/default-role and untrusted external Godot gates
remain closed. The main functional priorities are carrier messaging, live Walkie
and remaining app/settings behavior and acceptance, with games explicitly later.
