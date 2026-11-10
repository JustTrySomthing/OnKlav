# Setup, Power, Radio and Write refinements

Source changes following the utility icon refresh. These changes are now included in OnKlav0.100.28-rc1/code137; see RELEASE-0.100.28.md.

## Behavior

- Setup keeps a prominent 18sp Finish later action visible. Each incomplete step can be skipped for now without marking its permission complete. Skipped steps are local setup progression only; actual remaining approvals remain visible. After all remaining steps are skipped, Continue to Apps is explicit.
- Power has a Directions dialog with four numbered accessibility steps. Apps invokes GLOBAL_ACTION_POWER_DIALOG directly and leaves Apps underneath; there is no Power workspace route. Unavailable permission/service produces a small setup prompt.
- Compress rotates only the interior artwork 90 degrees, with vertical compression arrows and unchanged outer rails.
- Write adds a minus/Insert horizontal line control immediately before Image. It inserts a real line at the caret without deleting selected text, preserves surrounding formatting, places the caret on the following paragraph and participates in autosave/Undo. Rules persist as validated document marks and export to HTML, DOCX paragraph borders and PDF; DOCX import restores the rule. Rule width follows editor size.

## Radio diagnosis and repair

Two concrete transport problems were identified. Station resolution opened a live audio GET, closed it, then playback opened a second GET. A server rejecting overlapping or repeated listeners could therefore fail the first playback attempt. In addition, releasing ExoPlayer did not explicitly cancel its active HTTP calls; a loader blocked on a socket could outlive the release fence.

Resolution now transfers a successful direct audio response to the player without consuming its bytes. Playlist/HLS handling remains separate. Each player owns its pending and active HTTP sources; retirement cancels calls before releasing the player. Unclaimed responses expire, permission checks remain on open/read, and stale sources cannot reopen after retirement. Pending leases are released by matching owner/handle so an old queue cannot close a new station.

The regression test uses real localhost sockets with a test-only connection hook; production public-network restrictions are unchanged. It reproduces the old duplicate-GET 503 and verifies the new handoff makes one request, preserves first bytes and ICY headers, survives switch/reopen cycles, and cancels a blocked read. This establishes the transport defects and their repair, not physical Samsung/carrier/decoder qualification.

## Verification

Focused Android host tests cover Radio handoff/recovery, setup progress/skip/help and system Power dispatch, Write insertion/persistence/export and toolbar layout. See setup-radio-write-checks for actual reports. Initial compilation caught and corrected SetupStepCard trailing-lambda compatibility; the first test pass caught an ambiguous duplicate Enable Power label in the test assertion, subsequently scoped to dialog instructions. No Rust sources or native binary hashes were changed.

Result: 34 unique focused tests passed: 24 Radio, 6 Power/setup and 4 Write. The initial run passed33/34; after correcting the duplicate-label assertion, all6 setup tests passed. Final Android production and unit-test Kotlin compilation succeeded. Full lint and physical device testing were not run in this task.

APK follow-up: before packaging 0.100.28, review caught that the transferred response inherited the directory client’s 12-second whole-call timeout. Live resolution now uses a separate client with no whole-call deadline and bounded connect/read waits. All6 real-transport/configuration checks passed again, including the new deadline check. The original34 feature tests remain retained;35 unique focused checks now have passing evidence.
