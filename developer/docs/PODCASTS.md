# Podcasts — host80+

Podcasts is its own signed SDK app (`app.onklav.podcasts`) in the OnKlav host.
Radio remains independent, including its favorites and recents.

Discover searches a bundled starter snapshot of Podcast Index's MIT sample
export. 484 public HTTPS feed entries are included; this is neither the full
Podcast Index nor a live chart. Search works offline and calls no directory API.
`assets/podcasts/catalog-source.json` records the source/hash. Reproduce it with
`scripts/import_podcast_catalog.py`; the sample source may change over time.
There are no accounts, API keys, API fees, paid plans or app listening quotas.
Publisher availability, bandwidth and any limits imposed by their servers remain
outside OnKlav's control. Only public episodes are playable; no paid content is
unlocked. Add a public HTTPS RSS URL under My library for shows outside the snapshot.

A selected feed is read directly, with up to five validated public HTTPS redirects,
no authentication/cookies, an8MiB document bound and a12-feed/5minute memory cache.
RSS2 audio enclosures (MP3/AAC/M4A/OGG/FLAC/WAV) are projected20 per page, without
requiring enclosure lengths. Unsupported/HTTP-only entries are skipped, DTDs
rejected, titles rendered as text. The guest never gets audio URLs. Selecting an
episode resolves public audio redirects and hands an owner-bound in-memory lease
to the shared player. Every decoder read and control checks current capabilities.
Network concurrency is bounded for resources, without per-user request quotas.

My library follows shows locally using revision-checked private collection writes.
Feed/library failures retain saved data. Player controls offer Play/Pause/Stop,
seek,−15/+30seconds,0.75–2× speed, sleep15/30/60minutes and cancellation. The shared
host saves positions; seeking depends on the publisher supporting the host's
verified HTTP Range/ETag profile. Notification/headset/background controls reuse
the host service. No complete download/offline-audio feature is claimed.

Bundled apps get their declared OnKlav capabilities on installation and once on
upgrading to this host. This includes Radio's audio/radio access and Podcasts'
audio/library access. Later revocations are preserved. Android system prompts
cannot be silently granted and appear only where Android requires them.
External packages still use explicit signed-capability review.

Phone audible playback, interruptions, notification/headset/background behavior
and real provider availability require physical-device acceptance.
