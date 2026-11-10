# Background work, downloads and shared playback (working source)

These APIs require the matching unreleased host source. No SDK/APK release is
implied. Games are outside this API.

## Durable jobs

Declare `background.tasks`, `storage.private` and `network.http`, with signed
exact HTTPS origins. Each capability is revocable. Creating a task requires a
user tap; listing/status can run on opening the app. Android runs host-owned
handlers; it never runs guest Wasm in the background.

```rust
jobs::schedule("sync", http::get("https://api.example.org/items", "unused"), 60, "queued")
```

Read `reply.value.id`, then call `jobs::status(id, "updated")`, `jobs::list`, or
`jobs::cancel`. A repeated app-owned key replaces the task; reuse completed keys
rather than accumulating records. Limits are eight records per app,32 globally,
and three execution attempts. Delay is0–30 days. Request methods are GET/HEAD;
mutating background requests are rejected. Optional `unmetered` and `charging`
boolean arguments add Android constraints. All jobs require battery-not-low.
Android delivery is inexact, may be delayed, and is not a receipt. HTTP jobs have
a30-second call budget; each download attempt has a90-second network budget.
These budgets and the retry limit are constraints when sizing provider files.

Status contains id, key, kind, state, bytes, optional total/file/error and attempts.
States are queued/running/retry/completed/failed/cancelled. Explicit cancellation
returns its acknowledgement and removes that record from status/list, freeing
its slot; keep any desired cancellation history in your app. A completed HTTP job
returns a private immutable JSON file containing the bounded HTTP response.
Use `blobs::read` to inspect it. Unknown Content-Length is reported as total=-1;
show indeterminate progress. Cancellation removes unfinished staging, not saved
completed files. Each callback checks current installation, publisher, package
hash and grants. Updates invalidate stale execution; removal/permission changes
cancel tasks. Android persists jobs across reboot; host recovery repairs missing
registrations without creating another guest execution. No exact timing promise.

## Downloads

```rust
downloads::Download::new("chapter", url, "Chapter.mp3", "audio/mpeg")
    .max_bytes(64 * 1024 * 1024)
    .sha256(expected_hash)
    .unmetered(true)
    .start("queued")
```

The same job status/list/cancel APIs apply. The builder also offers cache,
charging and account. The file limit is1GiB, subject to the user's existing
private saved/cache quotas. Default maximum is64MiB. Increasing max_bytes does
not increase a storage quota. Each transfer uses fixed64KiB staging chunks.

Interrupted data resumes only with a strong ETag, If-Range and an exact validated
Content-Range/length/total. A fresh200 response replaces partial staging; weak or
absent validators restart from zero. Nonidentity content encoding, unexpected
redirects and invalid ranges are rejected. SHA256 is checked when supplied;
completion always computes a digest and commits through the existing immutable
file store. An unfinished file is never presented as completed. Keep SHA256
when the provider supplies a trusted checksum. A returned file handle is scoped
to the originating app; no host filesystem path or bearer token is returned.

## Shared player

Declare `audio.playback`, plus `storage.private` for private files and
`network.http`/signed origins for remote streams. Add `accounts.connect` when
using a connected account. Start from a tap:

```rust
media::play(vec![media::file(file_id, "Chapter 1")], "started")
// Render in your Screen body:
media::player("playback")
```

Use `media::stream(url,title)` for HTTPS, or wrap it with `media::account`.
The native component supplies live progress, seek, queue Previous/Next, Pause,
Stop,0.5–3× speed and an aspect-preserving video surface. It follows host colors,
fonts and layout constraints. Apps do not recreate focus, player, service,
notification, media-session, headset/noisy-output or lock-screen behavior.
The queue holds1–64 items. Programmatic helpers include status, control, seek
and speed. Raw media.play accepts optional index, position_ms and speed.

Only one host SDK player runs at a time. Playing another item relinquishes old
resources. Paused/error/completed sessions are released after five minutes.
Deleting app data clears its playback positions, including delayed-stop races.
Progress is saved periodically and on control changes; starting the same source
again resumes it. Process death releases playback; it does not silently restart
audio. Audio-focus loss and noisy-output events pause. Starting from background
without Android's foreground-service eligibility may fail; open the app and retry.

The decoder receives a guarded MediaDataSource, never an unrestricted remote URL.
All network reads use public-DNS HTTPS, no cookies/redirects and live grants.
Remote seeking requires range support plus a strong ETag. Sequential live streams
can have unknown duration and cannot be seeked. Supported formats depend on the
Android decoder: progressive MP3/AAC/audio/video are intended; HLS/DASH manifests
are not supported by this implementation. Unsupported formats produce a visible
failure and release resources. Export, arbitrary device files and arbitrary LAN
access are not granted by playback.

## Qualification

Focused host tests cover broker ownership, encrypted accounts, resume/integrity,
job recovery/cancellation/limits, guarded decoder reads and lifecycle behavior.
Real provider login, OEM codecs, screen-off playback, hardware focus/headset and
lock-screen behavior still require Android hardware acceptance. They must not be
reported as passed from a fake transport or player.
