# Radio connection feedback and controls — 0.87

Implemented source update; final checks recorded in radio-controls-verification.json. Radio classifies the last connection attempt without claiming permanent closure or knowing a broadcasting schedule. HTTP404/410 and unusable audio may be hidden for 24 hours; busy, restricted, timeout and connection problems remain visible. Retry uses the attempted station. A centered failure dialog offers Retry, Hide for now and Close.

The themed settings dialog provides automatic hiding, show hidden stations, unhide all and hide selected station. Options persist; manual hiding is honored when automatic hiding is disabled. Successful playback clears the old failure. Favorites and recents remain saved.

No APK requested or produced. Physical phone/provider playback remains unverified. Preserve released0.84APK and all earlier work.

## User experience

A failed connection opens a small dialog showing the station and the reason, with Retry station, Hide for now and Close. Timeout, offline/DNS, secure connection, restricted access, busy server and unusable audio messages avoid technical codes. Hidden stations return after 24 hours; confirmation tells users where to restore them sooner. The settings gear retains waveform and directory refresh controls, with a simple station visibility section. Favorites and Recent retain their saved entries even when Browse hides a row.

HTTP429/503 means busy or temporarily unavailable; the app does not claim a precise listener limit. HTTP401/403 means access restricted, without assuming geography. HTTP404/410 may mean off air or a changed address. No global active probe or schedule inference is performed. A last-attempt label stays briefly under a station; successful playback removes it.

## Validation

281 Rust core checks,129 shared controller checks and13 selected Android cases passed across focused runs. Rebuilt source-matched Linux/ARM64 JNI and16 signed included apps; no new Android signing identity. Final lint passed with0errors and181warnings; media lifecycle checks passed. Actual logs are recorded in the verification report. Build plan reuses current SDK/native/game artifacts. No APK assembled for this source-only update; phone playback remains unverified.
