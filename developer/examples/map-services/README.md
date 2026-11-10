# Map cache and adapter fixture

Run `python3 developer/onklav.py verify developer/examples/map-services`, then
repeat with `--scenario developer/examples/map-services/scenario-denied.json`.
The demo seeds a synthetic tile response, reads local/offline cache, parses clearly
labelled Nominatim/OSRM fixture data, opens a route overlay and clears cache.
The last scenario step reaches a real pending signed HTTP request; it does not
supply a fake Android reply or contact a provider. Replace the example.org provider
configuration and signed manifest origins before real use. No public service is
enabled by default. See [Maps](../../docs/MAPS.md) and
[Geocoding/routing](../../docs/MAPS-GEO.md). Never present fixture data as a live map.
