# GitHub updates

OnKlav checks its public GitHub releases once per day when opened and online.
Settings → Check for updates also offers a manual check, Download update,
progress/cancellation, and Install update. Automatic checks can be disabled.
Downloads only start after the user chooses them. Release candidates are included
by default when running a release candidate; the setting can be changed.

The first installation of 0.101.53 must be downloaded manually. Earlier versions
do not contain the updater. Subsequent compatible releases can use this flow.

## Release requirements

- Publish a non-draft release in JustTrySomthing/OnKlav with a tag such as
  `v0.101.54-rc1` or `v0.102.0`.
- Attach exactly one optimized APK named `OnKlav-0.101.54-RC.apk` for an RC,
  or `OnKlav-0.102.0.apk` for a stable release. SDK-only releases are ignored.
- Keep GitHub's SHA-256 asset digest, the original Android application ID and
  signing certificate, and increment Android versionCode as well as versionName.
- The updater compares numeric versions, RC numbers and stable releases rather
  than relying on release creation order or GitHub's stable-only latest endpoint.

Checks use the public releases API without GitHub login or credentials. Network
operations use bounded HTTPS requests and allow only GitHub's exact API/download
hosts. APK downloads are limited to 256 MiB and ten minutes; incomplete, altered
or oversized bytes fail verification. A cancelled download is never offered for
installation. The downloaded APK is private temporary data, with its own narrow,
non-exported FileProvider path and a read-only URI grant to Android's installer.

Before installation, OnKlav checks size, SHA-256, package ID, newer versionCode,
matching versionName, Android compatibility and equality with the installed
signing certificate. Android performs the installation and requests user
confirmation. Where needed, the install button opens Android's per-app install
permission screen and continues after permission is granted. Existing app data
is retained through Android's in-place package update; uninstalling is not part
of this flow. A verified download can be restored after process restart.

Automatic checks do not interrupt a conversation/game. An update notice appears
on the Apps screen, and can be dismissed per release. An unsuccessful automatic
check waits at least an hour before another automatic attempt; manual checks are
always available. This does not schedule checks while OnKlav is closed.

## Verification

OnKlavUpdatesTest covers numeric/RC ordering, channels, draft/SDK-only releases,
metadata and redirect rejection, download byte limits/checksum/truncation,
cancellation, signer/package/version/Android gates, cached corruption and the
read-only installer URI. UpdateSettingsTest checks the Settings controls.
Physical-device package-installer acceptance remains a separate check.

References: [GitHub releases API](https://docs.github.com/en/rest/releases/releases),
[Android package manager](https://developer.android.com/reference/android/content/pm/PackageManager),
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider).
