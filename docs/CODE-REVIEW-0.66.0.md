# OnKlav code review — 0.66.0

Reviewed the 0.65 production changes and adjacent provider, storage, cancellation,
notification/transport, speech and web recovery paths. Fixed the concrete issues
below. This is a bounded source review, not an exhaustive audit or independent
security certification. Games and the existing interface design were preserved.

| Severity | Finding | Correction |
| --- | --- | --- |
| High | Drive provider save rejected a same-file request, but its UI failure handler then unconditionally deleted the destination. An inaccessible source or a post-copy preference failure could also trigger deletion. | Cleanup now belongs to the copy operation and runs only after opening that destination for writing. Validation happens before any open/delete. Saving shortcut metadata cannot delete a successfully copied file. |
| High | Different SAF tree/document URIs can identify the same document. The prior URI-inequality guard could allow truncating the source through an alias. | Compare query/fragment-free identities and, within an authority, decoded document IDs before opening either stream. |
| High | The 0.65 duplicate-name guard reserved only one UTF-16 unit for the stem. A leading supplementary Unicode scalar needs two; a shortened extension could also end with whitespace. Both could publish an invalid name and make the next Files load fail. | Reserve the complete first scalar and trim the shortened extension's end while retaining the 180-unit limit and existing file bytes. Maximum-length integration cases cover ASCII, emoji stems and long extensions. |
| High | Overlapping Drive uploads could both pass the pending-record check before awaiting generated IDs, then overwrite the single recovery journal. Cleanup could overlap another transfer too. | A process-wide coroutine mutex serializes upload and pending-file removal. The pending check and whole operation share that lock; cancellation is checked at request boundaries. |
| Medium | Collision-name generation repeatedly scanned and lowercased the directory for every suffix, becoming quadratic for many equal base names. | Build one case-folded occupied-name hash set per lookup. A 9,000-collision regression finds the next free name. This is an algorithmic correction, not a measured handset speed claim. |
| Medium | Drive journal/account/shortcut commits and MIME lookup ran on the UI thread; file selection could leave upload controls enabled before metadata was ready. | Move transfer/journal operations, account/shortcut writes and provider metadata queries to IO. Busy state covers selection metadata. Report progress only when its integer percentage changes. |
| Medium | Non-JSON HTTP error pages hid the actual status, preventing otherwise-valid 404 cleanup. A saved unavailable account could force reauthorization to that same account indefinitely. | Preserve bounded non-JSON error status while still rejecting malformed successful bodies. Expose explicit account selection even without a current token; synchronous authorization failures leave a recoverable UI. |

## Verification

- 187 Rust checks pass; host JNI rebuilt from the exact current core source.
- 38 selected app Android checks and 12 shared Android checks pass.
- 237 distinct checks total; zero failures or skips. Android test invocation
  completed in 30.0 seconds; final runtime JAR matches compiled Kotlin classes.
- New provider cases preserve same-file/query/tree aliases and preexisting target
  data on source failure, and remove only an opened partial-copy target.
- New Drive cases cover simultaneous callers, cancellation before public sharing,
  retained recovery identity, account-bound cleanup, malformed/oversized responses
  and HTTP status handling. These use fixtures and send no real uploads.
- Existing upload byte/order, media/provider copy, notifications, permission loss,
  removal/transport recovery, shared speech and stale web callback checks pass.
- SDK build plan reuses current signed packages; games and publisher identities
  remain unchanged. No APK or replacement Android signing key was made.

Exact evidence: source-verification.json and evidence/v0.66.0. review.patch records
source/test changes against the saved 0.65 checkpoint. No full UI or emulator loop
was used, and passing checks were not rerun without a new reason.

## Limits

Real Google authorization/upload and OEM provider behavior remain unverified.
Google project configuration, phone/carrier/two-phone acceptance and SDK release
qualification remain as listed in TODO.md. No measured startup/frame-time/audio
or complete original-app parity claim follows from these host checks. Invalid
external data still fails closed; these fixes do not reinterpret arbitrary corrupt
indexes or promise a remote upload/delete transaction.
