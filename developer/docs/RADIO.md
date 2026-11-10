# Radio (host79+)

Radio is a separate bundled SDK app (`app.onklav.radio`), with Listen, Explore and
Favorites, its own private collections and recent stations. It shares the native
player engine with other audio apps. Starting another app's audio replaces the
current player; Radio's sleep timer is cleared on that change.

No app account, API account/key, paid API, streaming-service login or subscription
is used. The native broker queries Radio Browser's public directory directly.
Audio comes directly from the selected station. There is no audio relay server.
The directory is community-run and has no availability guarantee.

`radio.stations` is an explicit revocable host capability. The current host uses public Radio Browser and Icecast catalog sources, with bounded network reads, cached discovery, owner rate limits and station health feedback. The current playback transport supports public HTTP/HTTPS progressive audio, ICY metadata, HLS and bounded PLS/M3U resolution, subject to device decoder support. DASH, private-network targets and credential/cookie injection are not provided. Ordinary network.http remains signed-origin HTTPS-only; Radio's stream allowance does not broaden that API.

Play takes only a directory UUID, also requires audio.playback and a direct tap.
The host re-fetches that exact record and validates a bounded public HTTP/HTTPS redirect chain.
Credentials/cookies are never added; HTML/login replies are rejected. The guest
never receives stream URLs. Host-only memory leases bind source to installed app,
publisher/hash and a random handle; every decoder read rechecks grants. Ordinary
signed HTTP keeps its existing exact-origin restrictions. Removal/update/grant
changes cancel requests and active playback. No passwords or API keys are saved.

The compact player remains docked while browsing; it offers pause/play, stop,
retry, favorite and a15/30/60-minute sleep timer. Native background media/session/
notification/headset controls are reused. Radio never restores a saved byte/seek
position, or exposes a seek/speed control. Song metadata/artwork is not invented:
this first version shows station identity; ICY song titles, HLS and remote artwork
remain separate extensions. Sleep stops the current player only and does not
survive process death; process death also ends this in-process player.

Favorites use independent optimistic collection records and paged loading;
recents retain the last12 station summaries. Failed saves keep existing data and
show an explicit error. No podcast feeds, episodes, subscriptions or downloads
are mixed into Radio. Podcast/reference-listening source is preserved.

Permission setup: Settings > Apps > Radio grants Radio stations and Audio
playback. These are host permissions, not API registration. Station directory
attribution stays visible. The app starts locally with no automatic playback.

Evidence and actual unclosed phone/codec/background gates:
`docs/radio-app-verification.json` in the source root.

## Phone check for this test build

Open Radio in Apps. If requested, allow Radio stations and Audio playback in
Settings > Apps > Radio; these are OnKlav capabilities, not API registration.
Choose a genre or search a station, tap it, save a favorite, and reopen Radio
to check that it remains saved. Check Play/Pause/Stop, Retry after disconnect,
notification/headset controls, background playback and the15minute sleep timer.
Confirm that Podcasts and its saved data remain separate. Public streams can
be unavailable; try a second station before diagnosing the player.

Automated checks do not establish physical-device decoder/audio/background
acceptance. This environment cannot resolve public station hosts for the full
Android live check; the opt-in check is retained for an unrestricted network.
