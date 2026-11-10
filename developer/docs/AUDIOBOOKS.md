# AudioBooks

AudioBooks is an included signed OnKlav SDK app, not another Android APK. It uses
`media_player` with `audiobook: true` and the declared capabilities
`audiobooks.library`, `storage.private`, and `audio.playback`. Any signed SDK app
may use the same scoped host component after capability review. OnKlav provides
its native file picker, book storage, catalog and playback controls. A rendered,
enabled player widget is required; `media_component` binds operations to the
installed publisher, package hash and live session.

Personal books are copied from Android-selected files/folders into private
storage, naturally sorted, with per-book progress and bookmarks. Sources are
never modified. Supported DRM-free audio: MP3, M4B/M4A, AAC, OGG/Opus, FLAC and
WAV (decoder support depends on Android). Nero and QuickTime M4B chapters are
parsed with bounded reads; malformed or unknown chapters fall back to file
navigation. MP3 ID3 chapter markers are not parsed.

Public LibriVox catalog search uses its documented HTTPS interface, twenty
results at a time, cached for thirty minutes with two seconds between catalog
requests. No credentials, key, signup, paid tier or per-user listening quota.
Only public LibriVox/Internet Archive source URLs are accepted, with checked
redirects, public DNS and no cookies or credentials. Provider availability and
rate restrictions still apply. Streaming seek requires a verifiable range
response; downloads provide reliable offline random access.

Downloaded files commit one chapter at a time. Cancel/restart retains completed
chapters and discards the incomplete chapter. Downloads use an explicit Android
data-sync foreground service with cancel notification. A process restart does
not silently restart a transfer: tap Download to resume. Timed sleep and chapter
sleep are separate; seeking cancels chapter sleep. Background playback uses the
existing OnKlav media service, audio focus, noisy-headset handling and media
session. Progress saves every five seconds and at explicit controls. Resume
rewinds five seconds. Bookmarks, speed and finished status remain on the device.

Private files/URLs never enter guest frames. Host-only UUID stream leases are
checked against app identity/hash and revocable capabilities on every source
read. They do not widen guest `media.play` or generic network policy. Removing
a book stops this app's playback; deleting app data clears private copies and
progress. Retaining app data preserves the library for the same publisher.
