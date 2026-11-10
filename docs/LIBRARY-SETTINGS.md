# eBook library awareness and Settings — 0.89

Complete source update. No APK requested for this step; preserve released0.88 and original signing identities.

Gutenberg search now compares numeric catalog IDs with the caller's entire committed eBook collection, independent of the visible40-row library page. Host-owned bounded reads use only that caller's private scope. New entries retain gutenberg_id; old downloads are recognized from their existing Gutenberg-ID.epub blob names. Only committed, ready copies count. Existing duplicates are kept; nothing is silently deleted.

Owned results are disabled and dimmed with Already in your library. The guest also rejects preview/acquire actions for owned results. Acquisition rechecks ownership before network/staging and before finishing; completion marks the current result immediately. Returning to Browse refreshes ownership from disk, including after removal. Current library formats and Rust core API are retained; existing source-matched JNI is reused.

Descriptions accept both Atom text content and Gutenberg's nested XHTML. A short summary is shown below the author and above Download. Empty legacy summary caches are bypassed with a versioned cache; missing summaries have an explicit fallback instead of a blank area. No invented synopsis is used.

Settings has separate Help, About and Advanced entries. About contains About OnKlav, Privacy Policy and Open-source Licenses. Help provides local topic guides and a verified Android Help link (https://support.google.com/android/). The previous Help troubleshooting items move to Advanced; other normal settings retain their locations. Nested menus return to their parent and keep the existing centered dialogs and themes. The existing privacy controls are preserved.

134controller and11selected Android checks passed. Full lint passed with0errors and182warnings. One opt-in live Gutenberg EPUB compatibility case was skipped. Physical phone acceptance remains separate.

Validation uses the actual-provider XHTML structure, signed eBook/native storage/reader handoff,21MiB download, legacy IDs beyond the first40rows, caller scope, disabled row/no preview, description position, large text and nested Settings navigation. See library-settings-verification.json and evidence/v0.89.0. Arbitrary imported files without Gutenberg provenance are not assigned a guessed catalog ID.
