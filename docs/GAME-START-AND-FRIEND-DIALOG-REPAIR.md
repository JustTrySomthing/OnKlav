# Save lifecycle update

The save/launch lifecycle below is historical. Independent per-game storage, acknowledged save-before-close, safe New/Resume/Cancel and title-specific routing now supersede its global paused-game gating and retained background-renderer behavior. See GAME-SAVES.md for the current contract. Earlier gameplay/visual verification remains historical evidence.

# Game start and friend-dialog repair — 3 November 2026

Source correction after APK0.100.48/code157. No new APK has been built for this correction; the installed157 APK still contains the reported defects.

## Cause and recovery

The catalog rejected a start whenever a paused-game marker or pending/active friend session existed, without giving the user a route to it. A dismissed friend dialog also left its checking/outgoing record alive, blocking single-player starts until expiry. Retrying an expired record did not create a fresh verification window.

All catalog and Messages entries now resolve the existing match first, or reopen the saved game and its existing New/Resume/quit controls. With no current game, Single Player opens Naval Battle with the fresh-start flag. Pending friend attempts reopen the connection dialog so they can be continued or cancelled. The one-current-game rule and existing game checkpoints remain intact; starting another game never silently discards progress.

## Original interface restored

Inspected Inner Circle source235: GamesPane.java, ConnectionDialog.java, ConnectionState.java, ContactVerification.java and DialogUi.java. Reimplemented the native interface in Kotlin/Compose; no legacy Java app code is embedded.

- Choose your opponent: separate Choose contact and Enter number action rows with icons, plus Cancel.
- Invite by number: phone keyboard, validated Mobile number field, Invite and Cancel.
- Searchable, bounded lazy contact list; hold to reverify; independent verified labels and Click To Verify User/Reverify controls. Standalone verification never creates a match or blocks single-player play.
- Cats/Dogs side choice happens after recipient selection. Messages shortcuts skip the redundant recipient chooser.
- Connection starts automatically, showing the recipient, progress and the original Verifying User / Waiting for User to Accept / failure states. Cancel and failure-only Try Again use separate full-size actions. Removed the combined number/app-check/instructions form.
- Cancel durably ends only a pending attempt, cancels its queued jobs and tells the peer. An acceptance racing cancellation preserves the active game. Retry creates a fresh attempt and expiry window. Late replies to a cancelled attempt cannot revive it.

The readable SMS asks whether the recipient has OnKlav. Existing scoped SMS protocol, private fleets, engine and SDK APIs are unchanged. No real SMS was sent by these host checks; the tests use Android telephony shadows.

Verification results and evidence: game-start-dialog-repair-verification.json and evidence/friend-dialog-repair. Physical two-phone/carrier/Samsung acceptance remains separate.

All14 focused checks pass:8 friend broker/verification methods,3 session-marker methods and3 rendered native UI/routing methods. Production and all test sources compile; the runtime bundle matches Kotlin output. Opponent, number-entry and connection-failure renders were inspected. Native, signed SDK apps and shared game exports remain source-current.

Packaged and APK-verified in0.100.49-rc1/code158. The earlier source-only status above is historical; see RELEASE-0.100.49.md.
