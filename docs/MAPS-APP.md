# Maps — first-party OnKlav app

The signed Maps shell (`app.onklav.maps`) opens a native Kotlin workspace under the existing shared header, operating-system Back and themes. Its folder icon is in the shared header's right action area. Bookmarks and Directions are separate tabs in the saved panel; each saved item supports rename and confirmed delete. Only one OnKlav APK exists; no separate Maps APK or new JNI/core service is introduced.

## User workflows

- Search ZIP/ZIP+4, city/state, addresses, names or latitude,longitude. The existing GeoNames city/US ZIP data is reused in a compact asset for exact offline ZIP and coordinate lookup and city fallback. Worldwide street/place search uses Photon. Search is explicit, without per-keystroke requests.
- Tap Current location to request foreground Android coarse/fine permission. No background location reads. GPS/network fixes and recent fixes up to2minutes old are supported; approximate accuracy is shown. Declined permission, provider-disabled state and20second timeout retain manual search.
- Pan, pinch zoom, accessible zoom/pan actions, marker selection and long press to drop a pin. Actual XYZ tiles use OpenStreetMap and shared Mercator projection helpers. Raster and route data are real provider data, never generated geography. Credits stay visible outside the map.
- Nearby gas, hotels, restaurants, cafes, groceries, pharmacies, parking and EV chargers search around the map center. Photon OSM category filters, bounded radius, distance sorting and result deduplication are used. Coverage depends on OSM/Photon data; results are bounded, not an exhaustive business directory.
- Directions have explicit start/destination, current-location start, swap and driving/walking/cycling. FOSSGIS OSRM endpoints provide route geometry, distance, duration and instruction data. New Get directions requests refresh the route. Route preview and clickable instructions are supported, not voice navigation, live traffic or automatic rerouting.
- Save named places and routes, rename/delete, reopen a saved route without recalculation. Saved routes include timestamp, endpoints, travel mode, geometry and instruction list. Map tiles are not bundled with a saved route.
- Settings includes distance units, nearby radius, clear cache, Help and source/privacy credits. Phone locale does not change search input leading zeros.

## Providers and transport

Signed exact origins: `tile.openstreetmap.org`, `photon.komoot.io`, `routing.openstreetmap.de`. No API keys/sign-in. Native first-party transport uses SdkHttpPolicy for fixed-origin/public-HTTPS request validation and SdkFeedService public DNS checks. Live installation, private-storage and network grants are checked before/after fetch and during body reads. Redirects/cookies/automatic network retries remain off. Requests identify OnKlav and onklav.app. Coroutine cancellation cancels the OkHttp Call. Responses are bounded (512KiB/tile;2MB geocoding/routes).

Photon and routing operations are explicitly triggered, throttled per origin to one start per1.1seconds per process; text search is cached24hours, nearby1hour. New route refresh bypasses normalized query caching. Public best-effort providers can throttle or change availability. This is not a hosted service availability guarantee or unrestricted high-volume directory backend.

OSM raster requests cover only the visible viewport (up to64tiles), with two concurrent transfers and180ms settled-view debounce. There is no offline-area/bulk download/prefetch. OkHttp's bounded32MB cache honors provider HTTP caching headers; missing tiles show Retry. Search/route JSON cache is app-private and bounded80entries. Bitmap LRU64tiles is bounded (~16MB) plus current viewport references. Private snapshots are atomic; limits300places,50routes,12MB saved data. App data removal clears Maps private files/preferences and HTTP/query cache.

Source credits: © OpenStreetMap contributors, ODbL; Photon/komoot; OSRM/FOSSGIS; GeoNames city CC BY4.0 and US postal CC BY3.0 (existing source extract). Map Sources exposes copyright and Fix the map links and explains which actions send queries/coordinates to providers.

Reference policies/API checked2026-10-27:
- https://operations.osmfoundation.org/policies/tiles/
- https://photon.komoot.io/
- https://github.com/komoot/photon/blob/master/docs/api-v1.md
- https://routing.openstreetmap.de/about.html
- https://project-osrm.org/docs/v5.24.0/api/

No public Nominatim/OSRM demo defaults or Overpass backend are used; the existing generic SDK adapters' public-default restrictions are preserved.

## Validation

See maps-app-verification.json and docs/evidence/v0.95.0. Public sample queries use Nashville coordinates, not a user location. Real server responses are retained as parser fixtures. Host tests and UI checks remain separate from handset GPS, TLS/cache behavior, gesture/performance and driving/listening acceptance. User-requested OnKlav0.95.0 ARM64 APK assembled and verified with the original signing identity; includes Maps and Audio. Handset acceptance remains separate.
