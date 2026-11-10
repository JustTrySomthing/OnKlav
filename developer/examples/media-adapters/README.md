# Podcast/radio fixture example

Verify with `python3 developer/onklav.py verify developer/examples/media-adapters`.
For denial use `--scenario developer/examples/media-adapters/scenario-denied.json`.
Fixtures are synthetic. The final step reaches pending media.play; desktop does
not fake Android audio or claim station/provider acceptance. Configure real signed
origins before actual use. See [adapter guide](../../docs/PODCASTS-RADIO.md).
