# Podcast and radio adapters (working source)

`podcasts::fetch` uses the existing signed-origin HTTP broker (network.http).
Pass a successful text response to `podcasts::parse`; the local `podcasts.parse`
service requires storage.private and projects RSS2 XML into a channel title and
at most40 audio episodes. Feeds are limited to128KiB; malformed XML, DTDs,
ambiguous/duplicate enclosures, unsupported MIME, missing title/length and unsafe
addresses fail explicitly. Non-audio items without enclosures are omitted.
Titles are plain text; descriptions/scripts/artwork are not rendered or executed.
This is a bounded RSS2 audio profile, not Atom/Podcasting2.0/iTunes conformance.
Large feeds must supply a bounded page; there is no implicit truncation/pagination.

Each episode includes HTTPS enclosure URL, MIME and declared length (1 byte–1GiB).
Declared length is metadata, not verified download integrity. `podcasts::play`
passes the selected episode to the existing shared media player, retaining its
queue/progress/focus/lock-screen/cancellation behavior and live-grant checks.
Both feed and enclosure origins must be signed; discovering an enclosure does not
authorize it. Offline downloads remain the existing explicit bounded download API.

`radio::fetch` requests a configured Icecast status-json endpoint over the same
signed HTTP broker. `radio::metadata(response, selected_stream)` handles a single
source or up to32 mounts and matches listenurl exactly. It returns only plain
station name, artist/title and availability; it never switches streams, follows
server-supplied links or treats missing song data as current. Call refresh manually
or through a separately bounded foreground policy; no polling timer is created.
Clear/mark previous display stale on errors; a metadata failure must not stop
otherwise working audio. There is no automatic discovery, ICY interleaved metadata,
SHOUTcast, HLS/DASH or unsupported-codec fallback in this adapter.

`radio::play` uses the explicitly selected stream and existing shared media broker.
Audio playback requires audio.playback; remote streams additionally need network.http
and an exact signed origin. No location, account or station is chosen automatically.
Use the shared media_player widget for native controls, not a second player.

The media-adapters example parses conspicuously synthetic RSS/Icecast fixtures,
then reaches a real pending media.play operation. It does not simulate Android
playback or contact stations. Source checks and matching native artifacts do not
establish real provider/codec/OEM/background or physical-phone acceptance.
Full-host integration still needs the recorded unavailable dependency bytes.
Reference application workflows remain chunk38. No APK/SDK release.

References: [RSS enclosures](https://www.rssboard.org/rss-specification),
[Icecast JSON statistics](https://www.icecast.org/docs/icecast-latest/server_stats/).
