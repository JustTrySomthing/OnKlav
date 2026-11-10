# Weather fixture

Run `python3 developer/onklav.py verify developer/examples/weather`; denial uses
`--scenario developer/examples/weather/scenario-denied.json`. Seeds synthetic
weather through the actual private cache, changes units, reopens, and reaches
pending clock.read before any HTTP request. No clock/weather response is faked.
Synthetic data is labelled on screen. See [Weather](../../docs/WEATHER.md).
