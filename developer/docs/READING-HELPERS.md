# Working reading and playlist helpers

These pure Rust libraries are included in portable generated projects. They do
not add permissions, access Android services, or fetch any URL.

`onklav_ui::reading::page(text, start, characters)` returns a borrowed page,
UTF-8 byte start/end and total bytes. Text is limited to one megabyte; pages to
20,000 Unicode scalar values. Display through shared labels or speech widgets.
Advance using `page.end`; do not use Android UTF-16 selection offsets here.
The helper does not calculate visual pages, grapheme clusters or typography.

`reading::bookmark(content_id, revision, offset)` creates a small JSON value
that an app can save through collections. `reading::resume` restores the offset
only when content identity, revision and UTF-8 boundary match; otherwise it
returns zero. Choose an immutable revision, such as the committed content digest.
Persistence is explicit and requires the usual storage grant.

`onklav_ui::playlists::parse(text)` accepts UTF-8 M3U or PLS up to 64 KiB and
40 streams. It returns URL/title rows in playlist order (numeric order for PLS),
removing duplicate URLs. Unsafe URL syntax, credential URLs, non-HTTPS links,
IP literals, unsupported PLS fields and HLS segment playlists are rejected.
DNS resolution and exact-origin authorization still belong to the host broker;
parsing does not authorize a request. There is no redirect or network access.

These text and playlist helpers are one part of the SDK. Media playback,
downloads, EPUB and PDF, and [provider authentication](ACCOUNTS.md) use
separate host capabilities with their own permissions and preview limits.

`reading::find(text, query, start)` performs case-sensitive literal search. It
returns up to 40 nonoverlapping UTF-8 byte ranges; continue at the last end.
Text is limited to one megabyte and the query to 256 bytes. Empty queries and
invalid offsets fail. No regex engine or locale-dependent folding is implied.
