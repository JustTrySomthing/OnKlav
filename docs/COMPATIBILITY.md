# OnKlav compatibility checks

The 0.101.54 screen and startup sweep covers simulated Android 10 (API 29)
and Android 15 (API 35), using Robolectric with native graphics and the actual
Compose UI. These are framework/layout tests, not emulated Samsung/Pixel firmware
or a full Android device emulator.

| Profile | Workspace (dp) | Density | System text size |
| --- | --- | --- | --- |
| Compact phone | 320 × 568 | mdpi | 100% |
| Standard phone | 360 × 800 | xxhdpi | 100% |
| Large phone | 432 × 936 | xhdpi | 100% |
| Landscape phone | 640 × 360 | mdpi | 100% |
| Tablet | 800 × 1280 | mdpi | 100% |
| Compact with large text | 320 × 568 | mdpi | 150% |
| Extra-large text | 360 × 800 | xhdpi | 200% |

Each profile runs on both Android versions: 14 screen/text configurations.
Checks cover reachable Settings cards, the update dialog's margins and actions,
version text layout, scrollable number entry, navigation taps and selecting all
six built-in games from the game list. Separate checks on both Android versions
cover desktop Settings access, main-app startup, and native preflight/start UI
for all eight included games. The preflight checks deliberately stop before
starting Android WebView; they do not exercise its renderer.

The test harness measures windows against the configured display constraints,
scrolls lazy lists to bring controls into view, and allows one pixel of rounding
in fractional text metrics. Screenshots show simulated dialog surfaces rather
than complete phone displays.

## Shipped game rendering

A separate browser smoke test extracts games directly from the signed 0.101.54
APK. All eight titles are checked at 320 × 568, 800 × 360 and 800 × 1280 CSS pixels:
24 rendering checks. These start the production Godot exports, wait for the
host-control bridge, check canvas bounds and reject blank frames or script errors.
They use desktop Chromium with software WebGL, not Android WebView or a phone GPU.
They are startup/rendering checks, not complete gameplay or performance tests.

[Machine-readable results and screenshots](evidence/compatibility-0.101.54/)
include per-profile settings and JSON/JUnit results.

## Run again

With the project's normal Android/JDK/native test dependencies available:

```sh
./gradlew :android:app:testDebugUnitTest \
  --tests '*.Android10DeviceCompatibilityTest' \
  --tests '*.Android15DeviceCompatibilityTest' \
  --tests '*.Android10LaunchCompatibilityTest' \
  --tests '*.Android15LaunchCompatibilityTest'
```

The browser checks require Python Playwright, Pillow and a Chromium executable:

```sh
python3 scripts/check_shipped_game_viewports.py \
  --apk /path/to/OnKlav.apk \
  --chromium /path/to/chrome-headless-shell \
  --output /path/to/results
```

Run Gradle builds/tests sequentially. These checks supplement real-device testing.
SMS/MMS delivery, walkie audio, carrier/SIM behavior, package installation,
manufacturer battery policies, Android WebView compatibility, GPU drivers,
keyboard behavior and every individual app feature still need separate coverage.
