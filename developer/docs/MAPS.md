# Private raster map viewer (working source)

`maps::viewer` places a single `map_viewer` in a non-scrolling main body. Requires
`storage.private`. Supply explicit provider metadata, WGS84 center, XYZ integer
zoom0–19, up to25 private tile handles and32 labelled markers. A tile is an
immutable256×256 PNG/JPEG blob; maximum512KiB/tile and8MiB encoded per frame.
The Rust broker resolves only the current signed owner's visible enabled widget
and its private blobs. Kotlin validates canonical owner paths, checks image bounds
before decoding, and rechecks authorization before/after loading. No file paths,
Android URIs, location reads or network requests are exposed to the guest widget.

The native viewport places XYZ tiles with Web Mercator coordinates, wraps the
antimeridian and clamps latitude at±85.05112878°. Drag or use accessible direction
buttons to pan; Zoom changes raster magnification0.5×–4×, not XYZ data zoom. Reset
restores the supplied center. Markers have an accessible text list in Map details.
Missing coverage stays visibly blank with a no-data message when no tiles exist;
the renderer does not invent map data or silently switch provider. Replacing the
node supplies a new center/data zoom/coverage. Pan is bounded2048 screen pixels
per axis; output is clipped inside the reserved body. Lifecycle stop and owner
invalidation drop loaded rasters; partial-load failures release undecorated data.

## Provider and attribution

`maps::provider(id,name,template,attribution,copyright_url,osm,offline_allowed)`
requires a keyless HTTPS XYZ template containing one each of `{z}`, `{x}`, `{y}`.
Credentials, query strings, fragments and unknown placeholders are rejected.
Configuration is metadata, not permission to fetch. Templates are not requested
by this renderer, and `offline_allowed` does not implement an offline download.

Provider attribution always occupies a native footer outside the pannable map.
For OSM-derived data the host adds **© OpenStreetMap contributors** independently
of caller-supplied credits. Map details also shows the provider copyright URL and
OSM copyright URL as readable text. Apps must declare the true data source and all
required provider credits; a renderer cannot infer licence provenance from pixels.
Public `*.openstreetmap.org` tile endpoints require `osm=true` and
`offline_allowed=false`. Public OSM tile policy forbids offline-area/bulk download;
there is no public OSM network default or prefetch here. Use a provider whose terms
permit the intended workflow. Do not embed secrets in the template.

## Scope and evidence

Chunk31 is the renderer/provider/attribution source. Tile fetching, HTTP cache
semantics, bounded storage/eviction and offline-data policy are chunk32. Vector
PBF, terrain/rotation/pitch, geocoding, routing and turn-by-turn navigation are not
implemented here. Generated sample tiles are conspicuously labelled DEMO and are
not geographic data. No third-party tile service was contacted during checks.

Isolated host checks exercise real Android raster decode, projection, attribution,
canonical file denial and revocation before/after decode; controller/JNI adapters
refuse execution. The actual Compose viewer sources compile against real shared
components. This does not establish full-host JNI/UI integration, phone rendering,
TalkBack gestures, process recovery or sustained heap/performance acceptance.
See `docs/maps-verification.json` for current evidence. No APK/SDK release.

References: [XYZ tile coordinates](https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames),
[OSM attribution](https://osmfoundation.org/wiki/Attribution_Guidelines) and
[public OSM raster tile policy](https://operations.osmfoundation.org/policies/tiles/).

## Chunk32: bounded tile data/cache API

`maps::cached(map,x,y,offline,then)` performs `maps.cache.lookup` locally. Cache
identity includes app scope, publisher, full provider metadata and XYZ address.
Requests are limited to a5×5 neighborhood around the supplied center; there is no
area enumeration, download-city API or background prefetch. An app must supply
its actual viewed center and request only displayed tiles. General network.http
is not a licence-policy sandbox: apps remain responsible for provider terms.

On a fresh hit, use `file` in `maps::tile`. On an online miss,
`maps::fetch_cached_request(lookup,then)` returns an ordinary signed-origin
`http.request` using bounded binary hex mode and conditional ETag/Last-Modified
headers. After the native reply, call `maps::cache_response(map,x,y,response,then)`;
use the returned private file handle and preserve the real response headers.
There is no HTTP request on an offline lookup. It rejects providers without
`offline_allowed`; stale offline tiles are returned with `stale:true` only when
revalidation is not mandatory. Display that stale state. Missing/evicted tiles
return unavailable, never fabricated coverage. Public OSM endpoints retain the
existing offline prohibition; no bulk/offline-area support is implied.

Each cache accepts at most64 entries/16MiB encoded,256KiB per downloaded tile.
Native bitmap decode still requires256×256 PNG/JPEG (the passive renderer retains
its512KiB per-file ceiling for independently imported files). Cache metadata uses
atomic writes; approximate LRU eviction deletes only indexed tile blobs. General
private-cache quota can evict a tile too; the next lookup repairs a missing handle.
Clearing tile cache leaves other saved private files and other apps untouched.
Publisher/provider changes cannot reuse a prior cache key. Retained removal keeps
private data under existing host policy; delete-data removal deletes the scope.

Freshness considers Cache-Control max-age/no-cache/no-store/must-revalidate,
Age/Date/Expires, bounded to seven days. Unsupported/malformed directives default
conservatively; official standard OSM tiles without explicit freshness use the
policy's seven-day fallback. Clock rollback marks a tile stale. A304 requires an
existing tile and refreshes its metadata; absent304 freshness preserves the prior
lifetime. Network failures and non200/304 responses retain old cached data.
No-store responses discard an old cache entry and return no persistent file;
this file-backed renderer does not display a transient no-store response.
Interrupted metadata commit may leave a disposable blob to global cache eviction,
and explicit superseded-entry cleanup follows index replacement; the general
private-cache quota may independently evict a previously cached tile.

These are app-facing APIs, not a completed live-map application. See
[map-services](../examples/map-services/README.md) for signed broker/fixture checks
and [geocoding/routing](MAPS-GEO.md) for chunk33. Live provider, full-host binary
transport, device layout/recovery and sustained memory qualification remain open.

## Integrated viewport workflow

`maps.cache.viewport` takes measured logical width/height (1–1024 each) and
returns at most25 intersecting XYZ rectangles, with antimeridian wrap/dedup and
polar clamp. Hits are gathered in one local call; network misses are processed
sequentially. The public reference controller retains same-zoom coverage during
dataset pan, clears it on data zoom, and fetches only on explicit Load. Offline
lookup never fetches and respects provider policy. Optional `on_viewport` receives
measured dimensions and dataset navigation through a current owned widget,
without direct-tap authority. The legacy passive raster behavior remains when
no callback is configured. Configured search/result centering and route preview
are in the Controls drawer. No background/prefetch or offline-area API is added.
Actual provider/full Android/device/performance qualification remains open;
see docs/workflow-wrap-verification.json in the source root.
