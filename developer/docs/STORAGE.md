# Working-source storage and files

Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
Existing document services, shared file selection/import/export and RecordSchema
migrations remain supported. New `onklav_ui::collections` helpers use the same
revocable `storage.private` capability through the production Rust broker. They
are implemented in the current reference host; the protocol level alone is not a
phone permission or provider-availability guarantee. They expose no filesystem path or Android Context.

## Collection contract

Collections have a name, positive schema version, monotonically increasing
revision, ID-keyed JSON object rows and optional scalar equality indexes. A
missing collection reads as empty schema1/revision0; it is created only by a
successful transaction. IDs are logical record identifiers, not file paths.

| Service | Arguments | Result |
| --- | --- | --- |
| `collections.info` | `{name}` | `{schema,revision,count,indexes}` |
| `collections.usage` | `{}` | Current document/root-metadata bytes and fixed quota/collection limits |
| `collections.get` | `{name,id}` | `{schema,revision,id,data}`; data is null if missing |
| `collections.query` | `{name,index?,value?,after?,limit?,expected_revision?}` | `{schema,revision,items:[{id,data}],total,next,indexed}` |
| `collections.transact` | `{name,expected_revision,schema,indexes?,operations}` | Committed metadata |
| `collections.clear` | `{name,expected_revision}` | `{cleared:true,revision}` |

Transactions contain1–64 `put` (`{operation:"put",id,data}`) or `delete`
(`{operation:"delete",id}`) operations. Validation, index/schema changes and rows
are committed in one atomic collection snapshot. Invalid batches do not partially
delete or update old rows. The host serializes guest operations. A stale revision
fails: reload and reconcile; do not blindly retry and overwrite another screen's
changes. On an I/O acknowledgement failure, reload to establish whether the
commit became visible. Transactions do not span multiple collections.

Declare the schema version your app understands on each transaction. Older
versions cannot overwrite a newer stored schema. Apply deliberate migrations
before raising the schema; for embedded record formats reuse RecordSchema.
There is no automatic destructive migration or arbitrary background migration.
A migration affecting more than64 rows needs an app-specific staged strategy;
this API does not promise a single unlimited migration transaction.

Indexes name simple top-level fields and match exact JSON scalar values. Missing
fields are not indexed. Arrays/objects and oversized scalar keys are rejected.
This is not a full-text, SQL, join or case-insensitive search engine. Results are
sorted by record ID; pass `next` as `after`. For consistent multi-page reads,
pass the first result's revision as `expected_revision` on subsequent requests;
a changed collection must be reloaded rather than silently mixed into one view.
Clearing retains schema/index definitions and increments the revision, preventing
stale pre-clear writes from replacing new content.

## Limits and lifecycle

- 32 named collections per app;2000 rows per collection;8 indexes.
- 16KiB per row object;2MiB encoded collection snapshot;256KiB request arguments.
- At most40 rows and200KB of row JSON per page; `next` handles shorter pages.
- Collections share the private document/root-metadata budget (16MiB default;
 user-adjustable to128MiB).
 Existing media stores have their own limits; this is not an unlimited blob cache.
- Names use1–64 ASCII letters/digits/underscore/hyphen; IDs use1–96 ASCII
 letters/digits/dot/underscore/hyphen. Path traversal and symlinked files fail.

The runtime reads and validates a bounded snapshot and its indexes per operation.
This keeps atomic recovery straightforward, but is not a large streaming database
or a claim of constant-time disk lookup. Use bounded pages and measure actual
workloads. No new external database dependency was added.

Every operation uses the authenticated app's private scope. Same-publisher apps
remain isolated. Grant revocation invalidates sessions. Removing an app with
retained data preserves collections; deleting app data removes them. Existing
publisher pinning and signed update rules remain in effect.

```rust
use onklav_ui::{collections,serde_json::json};
let save = collections::transact("episodes", 0, 1, Some(&["show"]), vec![
    collections::put("episode-1", json!({"show":"example","position":0}))
], "saved");
```

Use the returned revision for later writes, never the sample's0 after creation.
Render errors with the shared status helpers while retaining edits. Do not store
passwords/tokens in collections; use the native credential vault.

## Private files and cache

`onklav_ui::blobs` exposes app-scoped handles, never raw paths. `begin` creates
staging, `append_bytes` writes1–65536 bytes at a supplied offset, and `finish`
checks an optional expected SHA256, syncs and publishes an immutable file. The
host streams the checksum without loading the whole file. `info` reports staged
size for resumption; retrying the immediately preceding identical chunk does not
append it twice. A conflicting offset fails. Staging acknowledgements are not a
power-loss durability promise; only successful finish publishes a ready file.

`read` returns at most64KiB with next offset/EOF; `read_bytes` decodes it safely.
Unfinished files cannot be read/exported. Files can exceed the8MiB guest memory
budget because only one bounded chunk enters the guest at a time. File size is
bounded to1GiB and the user's app quota. At most1024 handles are retained per app.
To replace a file, finish a new handle and explicitly update your record before
deleting the old handle. There is no multi-file/collection transaction promise.
Keep credentials in the separate vault, never these files or collection backups.

`list` pages40 metadata entries, including resumable staging. `delete` removes a
handle. `usage` reports private metadata, saved-file/cache bytes and current limits.
The default saved-file quota is64MiB, cache32MiB. User controls in Settings > Apps
> app options > Storage support documents16–128MiB, saved files16MiB–1GiB, and
cache0–256MiB (the UI offers standard sizes). Apps can read limits, but cannot
raise them or grant themselves permissions. Reducing limits rejects changes that
would require deleting saved files/documents. Shared host libraries are separate
from each app's private SDK store.

Cache files are explicitly disposable. When space is needed the host evicts the
oldest completed cache entries first; saved files are never automatic victims.
Active cache staging is retained unless explicitly cleared/cancelled. `clear_cache`
and the user's confirmed Clear Cache action affect only that app's cache, including
staging. Quota reductions may evict completed cache. An evicted handle reports
unavailable; apps must re-fetch/reopen instead of assuming permanent ownership.
This version uses FIFO, not persistent per-chunk access journals.

## Native import/export and collection backups

`blobs::import(cache, then)` opens the standard Android document chooser. It needs
`storage.private`, `files.external` and a direct user action. The host streams the
selected content into private staging, checks live authorization for each chunk
and returns only committed metadata. Cancellation/failure attempts to remove the
native staging handle; if process loss/revocation prevents cleanup, the app can
find and resume/delete unfinished staging through list/info. No old action token
is restored after process loss.

`blobs::export(id, then)` requires `storage.private`, `files.export` and a direct
user action. It streams a committed file to a destination the user chooses;
no content URI or host path reaches the guest. A failed export reports that the
destination may contain an incomplete copy. No silent retry or background export
occurs. Ordinary native picker imports/exports require no guest chunk loop.

`collections::backup(name, revision, then)` creates a private JSON backup handle
containing only that collection's schema/rows/index definitions. Export that handle
with the chooser above. Import a backup as a private file, then explicitly call
`collections::restore(name, revision, schema, handle, then)` to atomically replace
the target collection. The expected destination revision and declared supported
schema must match; damaged, oversized, foreign handles and incompatible schemas
leave existing data unchanged. Source backup revisions are never restored over
live destination revisions. Use a confirmation before replacing important data.
Backups do not include credentials, permission grants, publishers or action tokens.
Delete temporary backup handles when they are no longer needed.

These services are a data foundation. Streaming playback uses the media task;
network downloading/background jobs use their own later tasks. Shared OnKlav
Files browsing and the Android chooser retain their existing APIs. There is no
new APK, SDK distribution, universal database or unlimited storage in this task.
