# Files and Browser toolbar cleanup — 0.91.0/code99

Files hides edit pencils for protected/system entries, built-in roots and entries marked read-only or uneditable. Android document rows additionally check the current persisted write grant and provider mutation flags. Connected Android roots use a settings icon for Disconnect rather than an edit pencil. Normal editable personal files and folders retain their existing action menus. The Files header keeps Home and, inside folders, Parent; Refresh and the host overflow button are removed. Retry remains available on errors.

Browser has Bookmarks and a History clock in its header. History is a paged list of recent pages; clicking a row opens it. The clock toggles its drawer. Clear History confirms before removing visits and cached back/forward navigation while keeping bookmarks, cookies and website sign-ins. Full Clear Browsing Data in browser settings also clears this new history. History uses the existing scoped private collection API: at most100 unique recent pages, bounded to a15KiB snapshot, newest first with repeat visits moved to the top. It records visits from this update onward; older browsing records did not exist.

No new core API or JNI build is needed. The current WebView clears only this browser instance’s navigation history and cached tab snapshots; other apps’ web data remain independent. Storage failures remain visible in the drawer; opening History again refreshes its stored revision rather than blindly retrying writes.

Read Aloud's overflow button was already removed in0.90 and remains excluded. Its gear, transport and clipboard actions remain available. Original signing identities and all previous work are preserved. No APK requested or assembled for this source update.

Validation is recorded in toolbar-history-verification.json and evidence/v0.91.0. Physical phone/provider acceptance is separate.

Completed validation:134 controller checks;6 unique Android cases across focused runs, including4 final Files/history cases; full lint0errors/183warnings. All16signed included packages current. Native reuse verified. No APK assembled. Internal refresh remains registered behind the header action limit.

## APK release

OnKlav0.91.0-toolbar-history1/code99 ARM64 debug APK assembled and verified with the original Android signer, current SDK/native bytes and16KiB alignment. Prior focused tests/lint retained; no new source behavior changes. Physical phone acceptance remains separate. See apk-verification-debug.json and evidence/v0.91.0/build-debug.json.
