# Capability discovery and extensions

Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
This ADK inventory targets the current reference host. Run:

```sh
python3 developer/onklav.py capabilities
python3 developer/onklav.py capabilities camera.capture --channel working
python3 developer/onklav.py catalog secure-storage
```

The default `current` channel reports implementations in the reference APK. `released` retains the older baseline subset; `working` also reports implemented source. Preview services remain labelled as requiring device/provider checks. Availability never means authorized on a phone.
The tool reports no device probe or permission check. Retain the actual tested APK, guest protocol and source identity; do not infer device support from a version number alone.
Unknown capabilities fail rather than being advertised as supported.

## Compatibility rules

The contract revision describes inventory metadata; it does not change the
signed package schema or Wasm ABI. Additive tooling metadata is not a new guest
permission. Existing packages and signed min_host remain unchanged. Do not infer
historical per-feature minimum versions from the reference host build.

Keep released APIs compatible. A breaking guest ABI/schema needs a new identifier
and explicit migration. Additive native services need capability validation,
argument/result bounds, Android implementation, public helpers, live grant checks,
preview feedback, tests and release compatibility evidence before promotion.
Never declare a planned feature implemented merely by adding its name here.

## Reusable guest libraries

Use compatible Rust dependencies statically compiled into the app's Wasm module.
The existing portable vendor/lockfile workflow applies. Libraries cannot create
new Android powers: they use the caller's declared/granted capabilities and share
its memory, execution, package and state limits. Dependencies requiring a native
OS, threads or arbitrary Wasm imports are not compatible without a host service.
Check licenses, dependency versions and resource costs; verify the final signed
package with the production broker. Do not load executable native plugins.

## Native service extension process

Native extensions are reviewed host-source additions shipped in an OnKlav update,
not third-party APKs or downloadable native code. Propose a reusable operation,
not app-specific privileged logic. Each proposal must include:

1. Public operation and result/error contract, required capabilities and user action.
2. App/publisher/resource ownership, Android prerequisites and unavailable behavior.
3. Size/time/concurrency quotas, cancellation, lifecycle and recovery behavior.
4. Revocation, update/removal cleanup, origin/device/account boundaries and safe logs.
5. Rust broker validation and Android bridge plus public Rust helper/themed component.
6. Denied/unsupported/revoked/cross-app tests and required real-device checks.
7. Contract/catalog/version updates, preview fixture, example and promotion evidence.

Host reviewers must reject bypasses to Android Context, unrestricted filesystem,
sockets or host permissions. A permission on the OnKlav APK grants no guest access.
Do not weaken existing restrictions to make an unsupported dependency work.

## Current working additions

Camera still capture, secret get/set/delete and show/cancel/schedule notifications
are implemented WIP services. Diagnostic checks2 verified camera, immediate
notification visibility and Keystore write/read/cleanup on the S21+; that does not
qualify every service or scheduled delivery on every handset. Scheduled jobs are
inexact, bounded and host-owned. Secrets must never enter UI, diagnostics or
ordinary records. External app/publisher carrier routing exists in working source;
real two-phone delivery/receipt/reconnect/removal acceptance is still pending.

All other missing capabilities in the development plan remain unsupported until
implemented. No unrestricted sockets, arbitrary native imports or general Android services are exposed. Bounded HTTP, Bluetooth, torch and foreground-location APIs are documented in their dedicated guides.
