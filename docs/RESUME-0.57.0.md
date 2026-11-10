# 0.57.0 — RSS source adoption and reading position

RSS v3 is a signed Rust/Wasm app; only OnKlav is an APK. The host/core changes
require new source-matched Linux and ARM64 JNI, not relabelled old binaries.

Production adoption: SdkAppModel.open -> included RSS publisher + own rss/feeds
preferences -> Rust SDK adopt_own_feeds -> feeds::adopt_own -> atomic SDK feeds.json.
Storage grant/publisher and no active RSS session are checked. Existing feeds.json
wins unchanged. The entire bounded URL list validates before writing; duplicates
are removed, an explicitly empty list remains empty. Old preferences are retained.
The acknowledgement commits only after durable core success; retries do not
overwrite existing saved data. No network operation or Inner Circle data access
is used for adoption. Normal RSS refresh remains subject to network.feeds.

RSS controller now has separate article/bookmark page indices and a detail Back
action. Generic scroll_key/drawer_scroll_key presentation hints identify article,
list filter/page and drawer query; Rust validates their bounds and the Kotlin host
retains up to 32 offsets per model. No app-ID-specific scroll implementation,
artwork/icon/layout change or new permission is introduced. Process-death guest
navigation restoration and handset/provider acceptance remain open.

Evidence: source-verification.json, apk-verification-debug.json,
evidence/v0.57.0. Full Rust checks, source-matched JNI and focused host checks
are separate from audible/network/handset/carrier acceptance. MMS_READY is false.

Next bounded batch: Annotation gesture/provider/pending-action recovery review.
Four broader work groups remain in FINISH-NON-GAME.md; games deferred and
Social intentionally Hello World. Preserve source/TODO/SDK Starter identity.

The first Android invalid-list assertion expected open() to throw, but the
existing ViewModel reports errors through model.error. Rejection/retention were
working; the test now checks the user-visible error state and retries after
dismissal. Fixture setup also uses production remove/reinstall to ensure a
pristine SDK store. Both earlier assertion attempts are retained as evidence.

The first screen run was stopped at the added automatic performScrollTo step
in the paused headless harness. The check now performs a bounded ScrollBy, pumps
layout explicitly, compares the exact restored offset and returns to the top
with another bounded action. No timeout increase or repeated full suite was used.

The bounded screen assertion next ran before the asynchronous article frame
arrived: the preview contained the same body text. It now waits for the unique
article-title widget before checking position. Final UI checks and APK byte/source
verification run separately; unchanged production APK assembly is not repeated.

The screen check then exposed a real offset-save timing defect: disposal captured
the old ScrollState after layout had clamped it to zero. The renderer now records
active offsets through snapshotFlow, before the old state departs; outgoing drawer
snapshots also retain drawer_scroll_key. This production change requires final
APK assembly after the focused UI regression passes.

Final: 171/171 Rust, 4/4 focused import/memory checks and 1/1 RSS screen check
pass. The screen check proves the exact saved offset after Back. Fresh final APK
assembly after the production correction passed in 34.85s and verification in
3.38s; see build-debug.json and apk-verification-debug.json. No test or timeout
was removed/increased to make the final result pass.
