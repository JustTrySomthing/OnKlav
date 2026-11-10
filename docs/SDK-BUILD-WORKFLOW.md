# Selective bundled SDK app builds

The nine bundled SDK programs now have separate input fingerprints. Editing
Notes selects Notes; changing the shared SDK selects every affected program.
Unchanged apps reuse their existing signed packages, without Cargo, Rust or the
publisher key. An incorrectly copied Android asset is restored from the verified
package without recompiling or signing it again.

```sh
python3 scripts/build_sdk_apps.py --plan
python3 scripts/build_sdk_apps.py --app Notes --plan
python3 scripts/build_sdk_apps.py --app Notes
```

Use the existing publisher key through ONKLAV_APPS_SIGNING_KEY when an app really
needs rebuilding. Do not create a replacement key. App code, manifest or package
changes need a newer manifest version before they can update an installed app.
Identity and publisher changes are rejected. Successful app builds are recorded
atomically as they finish, so a later app failure does not erase earlier work.

Without --app, build every stale package and restore all stale bundled copies.
With --app, only selected apps are processed. An unselected stale app stays stale:
partial work never certifies it as current, and host assembly still rejects an
incomplete bundle. Shared build flags, source additions/deletions, manifest changes,
package corruption and missing assets participate in freshness checks. Old build
proofs are migrated per package; they do not force a full rebuild just for the
new build script. Source hashes are checked again after compiling/signing.

If the app uses existing host services, its .onklav-a can be installed/updated
inside OnKlav without generating another Android APK. New host APIs or renderer
components still need an updated host APK. This script builds the listed bundled
apps; it is not a general third-party project generator.

Planner/update guards are exercised with isolated fixture projects, including
one-app changes, shared changes, corrupt copies, partially updated proofs, old
flags, publisher changes and version monotonicity. Real unchanged bundled
packages were also reused successfully here without a Rust toolchain or key.
No changed guest program was compiled in this checkpoint because guest program
sources were not changed.

## 0.45.1 validation corrections

Host checks and APK planning now require complete records for all nine apps,
exact current input paths, valid per-app inputs/flags/packages and matching host
bundles. Standalone SDK trees require no Android bundle copies. Duplicate ZIP
entries and malformed proof/record shapes cannot certify cached reuse.

## Files services (host 0.47+)

Files is the ninth built-in SDK app; Counter is the tenth program and remains a developer example. See docs/FILES-APP.md in the source package (FILES-APP.md alongside the SDK documentation). The shared library exposes `library.list`/`library.info`, `library.folder`/`library.rename`/`library.move`/`library.copy`, Trash/restore/purge and host-brokered import/open/export/external services. `files.manage` controls shared OnKlav content; `files.external` controls user-connected Android folders; copying to Android also needs `files.export`. Use opaque entry IDs; host paths and URIs are not guest API. Writes and picker operations require a user gesture; capabilities and tokens are checked at completion. Text exports default to Documents/<app>, image exports to Images/<app>, public browser downloads to Downloads/<app>.

The package builder validates source manifest fields against the signed manifest while allowing the packer to inject the existing publisher identity. It preserves publisher and update-version continuity; do not create replacement signing keys.

## Isolated working native builds

Use `python3 scripts/build_native.py --working --test-filter sdk` after changing
Rust broker services in this WIP. It builds Linux plus ARM64, verifies actual LOAD
segment alignment, updates the diagnostic overlay and source-matched working proof,
and preserves released main JNI/provenance. A narrower filter records only that
selection; it is not a full-core test claim. No APK or SDK packages are assembled.
