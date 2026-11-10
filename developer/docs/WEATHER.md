# Current weather adapter and cache UI (working source)

`weather::Place` configures an Open-Meteo-compatible HTTPS forecast endpoint,
explicit coordinates/place label and attribution/copyright URL. The convenience
`open_meteo` constructor uses api.open-meteo.com; its exact origin must be signed
in the app manifest with network.http. No GPS, place discovery, API key or account
is selected automatically. A refresh discloses the selected coordinates only to
the configured signed provider. Provider/model/usage-plan acceptance remains open.

The profile requests current model temperature,10m wind and WMO condition code,
Celsius/kmh and UNIX seconds/GMT. The bounded128KiB JSON response must match the
requested units and contain finite plausible values and a nonfuture timestamp
(with five-minute clock-skew tolerance). Missing values, unexpected units, HTTP
errors and malformed data retain the previous cache, never fabricate zero values.
This is current model data, not a measured local sensor, forecast, alert or advice.
Known WMO codes display plain condition names; unmapped codes show description unavailable.

The shared native screen displays metric or imperial values with explicit units,
place and provider credits/URL. It never executes content or loads remote images.
One current-place snapshot is stored in an app-private optimistic collection
transaction. Provider/location/attribution identity must match before display.
A conflict or failed write keeps the old snapshot; no blind retry occurs.
Switching the configured place hides the old data. There is no implicit history.

No weather network request occurs on opening: saved data is labelled stale or
age unverified. Refresh first reads the host clock and then requests weather.
Check saved data age reads only the clock. Freshness is explicitly **as of the
last device-time check**, not a continuously ticking claim: more than30 minutes
since model time/fetch time, clock rollback, unavailable time or a failed refresh
marks data stale/unverified. Stale saved data stays visible with that label;
missing or mismatched data shows unavailable. There is no automatic polling,
background refresh, stale-to-current promotion after network failure or silent
provider switching. Unit preference lives in the current screen state; cache
stores canonical Celsius/kmh and does not reinterpret already converted values.

`weather::controller` supplies the native cached view, refresh/age/unit controls
and error text. `request/parse/rows/cache` are also reusable individually. Needs
storage.private for caching; clock.read itself is permission-free. Existing signed
HTTP/cancellation/revocation and private removal behavior remain authoritative.

Evidence: docs/weather37-verification.json. Parser/controller checks cover unit
conversion, expiry/clock rollback, wrong place, HTTP/malformed responses and failed
cache commit. The signed example persists synthetic fixtures and reaches real
pending clock.read without a fake native reply or provider contact. Full Android
HTTP/JNI, actual provider models, layout/TalkBack/lifecycle/process and sustained
performance qualification remain open. Core/Kotlin unchanged; verified JNI reused.
No APK/SDK release. Forecast/alerts/geocoding widgets remain outside this profile.

References: [Open-Meteo API](https://open-meteo.com/en/docs),
[attribution/licence](https://open-meteo.com/en/terms).
