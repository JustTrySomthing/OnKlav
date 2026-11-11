# OnKlav 0.101.57-rc1

- OnKlav checks for updates while it stays open on any tab. When OnKlav is in
  the background, Android schedules network-connected checks and can show an
  update notification if notifications are enabled.
- Available updates appear on the Apps tab and stay available across restarts.
  **Later** snoozes the prompt for 24 hours.
- **Update now** downloads and verifies the APK, then opens Android’s installer
  for confirmation. Android may ask you to allow installs from OnKlav the first
  time.
- OnKlav removes its cached APK after a successful install. If you cancel
  installation, OnKlav keeps the verified download for another attempt.

From an earlier release candidate, open **Settings → Check for updates** with
**Include release candidates** enabled, or install the signed APK below.
