# Calc, Weather and Convert

These are signed OnKlav SDK apps inside the single Android host. They use the same back-to-caller navigation, settings management, themes and permission broker as the other included apps. Their desktop symbols are `calc`, `weather` and `convert` (host90 or newer).

Calc offers arithmetic precedence, parentheses, signs, percentages, a decimal point and backspace. A direct percentage after addition/subtraction uses the current amount (200+10%=220); multiplying a percentage uses its decimal value. Invalid operations never produce NaN or Infinity. Previous/Next move through saved calculations, and History selects a calculation. Up to50 entries are saved privately. Long-pressing the output copies it. The display adapts to long results. The keypad is pinned low on sufficiently tall screens, with scroll fallback for compact screens or large text.

Convert is bidirectional: edit either value. It handles length (including feet+inches), mass, volume, Celsius/Fahrenheit/Kelvin, decimal/fraction, area, speed and time. Mixed fractions and negatives are supported. Volume names distinguish US and UK measures; ounces of weight are separate from fluid ounces. Fraction precision can be rounded to1/16,1/32,1/64 or six decimal places. Last category/unit preferences persist. Invalid values and impossible absolute temperatures clear the output rather than leaving a misleading result. Long-press the result to copy it.

Weather searches an offline GeoNames cities15000 snapshot (34,188 records). Results show city, region and country, with IANA timezone metadata. Latitude,longitude is an explicit fallback for smaller places; coordinate searches use UTC. No GPS access occurs. Up to8 selected places are remembered. Only selected coordinates are sent to the signed MET Norway HTTPS origin. Forecasts use the provider's SI values, converted for°F/°C; hourly and daily grouping uses the selected city's actual timezone, including DST. The7-day outlook's low/high values summarize available forecast samples. These are model forecasts, not a promise of live station observations.

Weather restores the last cached forecast and its timestamp, refreshes after15minutes when reopening, and keeps cached data on refresh failure. Explicit refresh is limited to once per minute when usable cached data exists. City search itself is offline. The settings panel provides source credits and unit controls. The app does not require a weather account or API key.

## Sources and attribution

- MET Norway Locationforecast2.0 compact: https://api.met.no/weatherapi/locationforecast/2.0/documentation . Forecast data CC BY4.0; https://www.met.no/en/free-meteorological-data . The host identifies OnKlav and its website in the HTTP User-Agent.
- GeoNames cities15000, countryInfo and admin1CodesASCII dumps: https://download.geonames.org/export/dump/ . CC BY4.0, https://www.geonames.org/export/ . The snapshot keeps identifiers, city names, country/region names/codes, coordinates and timezone; population determines search order. App settings link to attribution sources.

## Public SDK additions

`places.search` is a synchronous broker service for public offline metadata. Its only argument is `query`, a non-control string up to100UTF-8bytes. It returns at most15 records in `items`. It grants no location, network or private-data access and requires no capability.

`header_actions` selects up to4 visible toolbar actions (default2); text-only actions display their text.

Host90 also renders optional declarative utility presentation flags on existing widgets: `keypad`/`accent`/`muted_key` for themed button grids, `utility_display` for larger result labels, and `weather_display` with bounded forecast data for themed forecast cards. Label `long_action` uses the existing gesture-checked broker path; it never bypasses clipboard permissions. Old hosts are excluded by signed `min_host:90`.

## Verification boundaries

Core/controller checks and Android framework host checks are separate from actual phone acceptance. Weather fixture data came from the real public MET Norway endpoint over this environment's HTTPS transport. This does not verify direct Samsung networking, actual touch ergonomics or phone power behavior. Provider outages remain possible; cached data and retry paths are present.

Rapid keypad events queue through the existing serialized model operations while ordinary processing is busy. Pending host/device capability operations still block input. Repeated dismiss callbacks during a closing drawer/menu are ignored once the live frame has closed it.
