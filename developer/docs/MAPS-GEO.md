# Configured geocoding and routing adapters (working source)

`maps_geo::Provider::new(endpoint, attribution)` defines a keyless HTTPS endpoint
and required visible credits. Declare its exact origin in the signed manifest's
`network.origins` and request `network.http`. No provider, phone location, account,
API key or external navigation app is selected automatically. Search/routing
coordinates and queries leave the device only after the app explicitly issues the
request; present a clear Search/Route action. Do not query on each typed character.

## Nominatim-compatible search

Configure the full search endpoint, such as `https://geo.example.org/search`.
`search(provider,query,then)` returns the normal signed-origin HTTP effect with
UTF-8 percent encoding, jsonv2 output, limit5, and no extra address data.
`places(provider,response)` normalizes up to5 entries into labelled latitude/
longitude results with explicit attribution. Empty results remain empty; invalid,
non-finite, polar/out-of-profile or oversized results fail with a generic error.
`place_rows` uses native accessible record controls and includes provider credits.

Public `nominatim.openstreetmap.org` is deliberately rejected by this adapter.
Its shared-instance usage policy requires aggregate throttling, identification and
caching; the existing per-app HTTP budget does not qualify a global public-service
policy. Configure a compatible provider whose terms permit your intended use.
There is no autocomplete, background/bulk search or automatic provider fallback.
Store reusable normalized results through app-private collections when appropriate;
no search history or persistent geocoder cache is created implicitly.

## OSRM-compatible routes

Configure the route-v1 prefix, such as `https://routes.example.org/route/v1`.
`route(provider,profile,points,then)` accepts2–8 `(latitude,longitude)` points and
explicit driving/walking/cycling profile. The provider must actually support that
profile. Requests encode OSRM longitude-first pairs and request one simplified
GeoJSON LineString without steps or alternatives. No public demo endpoint is
configured: `router.project-osrm.org` is rejected pending provider qualification.

`route_result` bounds the response to128KiB, one route and2–2048 finite points,
returns distance metres/duration seconds, and preserves provider attribution.
`NoRoute`, rate limits, provider errors and malformed/oversized geometry produce
clear failures without reflecting raw provider bodies or query URLs. This is a
route preview, not turn-by-turn guidance or an offline routing engine.

`with_route(viewer,result)` adds geometry and separate provider attribution to a
shared map viewer. Rust validates points/credits again; Kotlin draws the path
inside the clipped viewport and splits jumps at the antimeridian. Raster data and
route data can have separate providers; both credits stay outside the map pan.
The supported geometry follows the current Mercator map latitude limit±85.05112878°.
Native result controls and explicit error text are reusable; final reference app
workflows remain chunk38.

## Evidence and limits

The included map-services example explicitly parses synthetic provider fixtures;
its final signed scenario reaches a real pending HTTP request without faking an
Android/provider reply. Parser checks are not live-provider acceptance. Existing
HTTP live grant checks, public-DNS filtering, cancellation, no redirects/cookies,
response limits and per-app budgets remain in force. Full Android transport/JNI,
actual chosen-provider responses and phone/TalkBack/process acceptance remain open.
No request was sent to public geocoder/router services during verification.

References: [Nominatim Search](https://nominatim.org/release-docs/latest/api/Search/),
[Nominatim public policy](https://operations.osmfoundation.org/policies/nominatim/),
[OSRM API](https://project-osrm.org/docs/v5.24.0/api/).
