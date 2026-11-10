# 0.62.0 — two source steps: Browser and YouTube backups

User correction explicitly skips APK assembly for this batch. Source version is
0.62.0-dev/code70; last delivered APK remains 0.61.0/code69. Do not relabel it.
Native libraries and Android framework tests can be rebuilt without an APK.

Actual path: Settings child routes -> generic LibraryBackupDialog/LibraryBackup
-> AppSdk.staging -> host SDK library_backup with scoped Browser/YouTube root ->
Rust archive validation -> browser/videos typed portable helpers -> atomic store.
No guest program/package/capability or original icon/layout changes.

Browser exports tabs/selected/home/newtab/engine/bookmarks. Existing tab IDs,
bookmark URL hashes, selection and settings win; pristine data takes all source
settings. Validate complete incoming and merged state, duplicate identities,
HTTPS/control/UTF-16/count/quota bounds before one atomic browser.json write.
No silent tab/bookmark truncation; overfull merge rejects unchanged.

YouTube exports only bookmarks/history, excluding viewer ID. Signed-out/canonical
URL/video IDs, unique identities, titles/times and capacities validate before one
atomic videos.json write. Current entries win; missing history sorts by time.
Cookie/profile/sign-in/permission/playback data stays local. No network request
or WebView launch is needed to back up/restore this metadata.

Evidence: focused archive checks then full Rust/native pass, provider/JNI round
trips reopening signed apps, and six-dialog Settings navigation. See current
source-verification.json for actual results. Source ZIP retains matched native
artifacts so a later requested APK need not rebuild unchanged Rust. No phone,
real-provider/account/carrier acceptance or performance claim is made.

Next: Walkie durable archive portability, then SDK backup integration review.
Four broader developer groups remain; games deferred, Social still Hello World.

Final: 183/183 full Rust +7/7 provider/JNI +1/1 six-dialog navigation checks
pass. Android host checks passed in 137.4s; navigation invocation in 33.6s.
APK assembly was not invoked. Source/SDK/checklist saving is the final action.
