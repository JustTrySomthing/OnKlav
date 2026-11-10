# Private map renderer sample

Run `python3 developer/onklav.py verify developer/examples/map-viewer` from the
source root. Each Create tap adds and saves one tile within the broker service-loop budget.
Creates four private synthetic256×256 XYZ tiles, stores handles in
an app-private collection and opens the shared native map renderer. Back/reopen
restores handles. No provider, network or location service is used. All tiles say
DEMO; this is not real geographic data. Android renders the private raster tiles;
desktop verification validates the real signed broker/widget schema and does not
fabricate a native map image. See [Maps](../../docs/MAPS.md) for scope and bounds.
