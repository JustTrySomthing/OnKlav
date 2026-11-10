> Notes has been replaced by Write in the host. The legacy Notes package/source is retained only for older SDK examples and backup compatibility; it is not an included desktop app.

# OnKlav App Development Kit (ADK) 0.4.1 — Developer Preview

Build an app's special behavior; reuse the platform for everything around it.
Authors ship signed `.onklav-a` packages installed on OnKlav's Apps desktop.
Only OnKlav is an Android APK. Included apps and third-party apps use the same
public Rust/Wasm controllers, UI renderer and capability broker. No Android
Studio, JDK, Gradle or NDK is required for external app development.

Reference Android host: **OnKlav 0.100.42-quick / APK code151**. Guest compatibility level: **96** (the value used by `min_host`), independent of APK update codes. GUI library: `onklav-ui`0.4.0.

See [current host and feature status](docs/CURRENT-HOST.md) before choosing APIs.
Low-level ABI helper: `onklav-app`0.1.0; runtime `rust-wasm-ui.v1`.
Games are outside this developer kit.

## Fastest start

The SDK is source tooling, not a compiler bundle. Python3.11+, Rust1.90+, the plain Wasm target and a native linker are required. After extraction run `python3 developer/ci_smoke.py` to check a fresh generated app through the real broker. See [installation](docs/INSTALLATION.md) for Linux/macOS/Windows.


Install Python3.11+, Rust1.90+ with Cargo/rustup and a native Rust linker
(C compiler on Linux/macOS; MSVC build tools on Windows). Initial Cargo builds
need internet to download dependencies. Extract the complete SDK starter and
open a terminal at its root. On Windows use `python` instead of `python3`.

```sh
rustup target add wasm32-unknown-unknown
python3 developer/onklav.py doctor
python3 developer/onklav.py new ../my-app --id org.yourdomain.myapp --name "My App" --template form
python3 developer/onklav.py verify ../my-app
```

`verify` builds, signs, installs and runs the generated scenario using the real
production broker in a disposable local test root. It creates and discards its
own test key/package. It requires no author key and never changes a phone's
installed apps or permissions. It does not simulate native provider success.

New projects contain portable, frozen SDK dependencies in `vendor`; moving the
project does not break dependency paths. Commit the source, vendor directory
and Cargo.lock. Use `--linked-sdk` only when intentionally tracking a live SDK
checkout. Run `upgrade PROJECT` to update a portable dependency snapshot; the
tool backs up the old vendor/lockfile and rolls back a failed update. Verify and
review before shipping. Do not put private signing keys in either the SDK or app project.

## Choose what to reuse

| Your app needs | Start with |
| --- | --- |
| A private document/editor | `--template document` |
| Searchable list/detail/editor/delete | `--template list` |
| Structured fields, defaults, validation and persistence | `--template form` |
| A private preferences page | `--template settings` |
| Full file management | `--template files` |
| File selection and image/video/audio viewing | `--template viewer` |
| Included app behavior | `notes`, `rss`, `reader`, `browser`, `youtube`, `annotation`, `social`, `walkie` |

Use `python3 developer/onklav.py catalog files` (or `speech`, `form`, `audio`, etc.)
to find components, APIs, permissions and guides. All supported widget kinds
are catalogued. The14 templates use independent app IDs/publishers, not built-in
privileges. Reference/native templates have startup-only scenarios; add tests
for their special behavior. Social remains the existing Hello World placeholder.
Cellular Walkie has external app/publisher routing in the current source, but real carrier acceptance is still pending. Local voice components are public.

- [Shared components](docs/COMPONENTS.md): screens, forms, lists, navigation, file/media reuse.
- [Cookbook and runnable projects](docs/COOKBOOK.md): short examples with real scenarios.
- [API](docs/API.md): every node, effect, callback, permission and limit.
- [Compatibility](docs/COMPATIBILITY.md): versions, migration, supported boundaries.
- [Testing](docs/TESTING.md): scenarios, failures and phone checks.
- [AI authoring](docs/AI-AUTHORING.md): instructions for an assistant building your app.
- [Distribution and dependency notices](docs/DISTRIBUTION.md).
- [Readiness](docs/READINESS.md): evidence and remaining qualifications.
- [Contract](contract.json) and [catalog](catalog.json): machine-readable inventory.

## What the host handles

The host supplies a rounded X dismissal button, title/actions, overflow, protected Close, system/keyboard
safe areas, theme/fonts, common dialogs and drawers. Full app pages and drawers
slide; modal dialogs pop subtly in the center. Header overflow keeps primary
and secondary actions reachable. Controls respect enabled/disabled state.
Long forms scroll and grouped controls adapt to screen width and font scale.

Use shared file selection and existing media/speech components instead of
writing a picker, player or platform permission flow. Apps receive opaque IDs,
not raw paths or Android Context. Private storage is isolated by authenticated
app identity. Users review and may revoke capabilities; native Android permissions
may also be required. Declaring a capability is not a grant.

## Give the app its own icon

`new --icon notes` uses a stock symbol colored by the user's theme.
`new --icon ./icon.png` embeds a signed custom icon. PNGs must be8-bit RGB/RGBA,
noninterlaced, at most128×128 and6144bytes, without animation, text or compressed
profiles. The tool checks CRCs and bounds decompression; Android checks chunks
and dimensions before decoding off the UI thread. Custom artwork keeps its
colors inside the same themed tile. Generated manifests declare `min_host:96`;
the host rejects packages requiring a newer build. The full symbol list is in
the contract. Old packages without either optional field remain supported.

## Release to users

Use your own reverse-domain ID; the examples are placeholders and `app.onklav.`
is reserved. Generate an author key **once**, outside SDK/project directories:

```sh
python3 developer/onklav.py keygen ../private-keys/publisher.key
python3 developer/onklav.py build ../my-app --key ../private-keys/publisher.key
```

Default output: `../my-app/dist/org.yourdomain.myapp-v1.onklav-a`. Build verifies
signature, install and startup without grants. `check PACKAGE` repeats that
package check; `test PROJECT` runs its scenario. Existing release files are never
silently overwritten by the developer CLI. Keep the publisher key private and
back it up; a lost key cannot update a pinned app identity.

Transfer the package to a phone. Choose **Settings → Apps → Install App**, review
identity/publisher/capabilities, install and choose grants, then open it from Apps.
To update, keep ID/key and increase integer `version`. Valid updates retain private
data and invalidate old sessions. Removal can retain or explicitly delete data;
publisher pinning remains. Safe old-package collection retains each registry
entry's current package, including removed apps with retained data.

## Working-source capability inventory

Run `python3 developer/onklav.py capabilities` for channel-aware discovery.
See [extensions and compatibility rules](docs/EXTENSIONS.md) and
[localized UI resources and state recovery](docs/UI-RESOURCES.md). Camera, secrets
and scheduled notifications are implemented WIP additions, not a released SDK
promise. The inventory does not probe a device or grant permissions.

Working source adds [structured private collections](docs/STORAGE.md) with
revision-checked atomic batches and bounded equality indexes, private large files,
cache management, scoped backup/import and user-controlled storage quotas. These
require the matching working host and are not a released SDK promise.

Working source also provides [bounded HTTPS APIs](docs/HTTP.md) with signed
origin declarations (`new --origin https://api.example.com`). The matching
host implements [account authentication](docs/ACCOUNTS.md), including
sign-in, refresh and authenticated requests. This preview still requires live
grants and Android and provider qualification.

## Practical limits

This is a developer preview, not a full arbitrary Android API or independently
audited production SDK. Arbitrary widgets and general background services require additional host APIs.
Native date/time field dialogs already exist and follow the user’s clock format. Working source already includes camera, secure
credential-vault operations and bounded inexact scheduled notifications.
Default-SMS setup, original-data adoption and privileged message handoffs remain
host-only. Device/provider/media/speech/accessibility/OEM/carrier acceptance,
Windows/macOS setup and release/store qualification remain open. The desktop
harness verifies the real verifier/interpreter/broker, not Compose rendering or
Android services. See readiness for exact checks; do not claim full OS parity.

## Working service reference applications

See [public service reference apps](docs/SERVICE-REFERENCE-APPS.md) for practical
Weather, Listening, Tile Map and Connected Mail source projects, alongside the
existing contract/fixture examples and their explicit qualification limits.

Included utilities Calc, Convert and Weather use the same signed public SDK model. See [utilities](../docs/UTILITIES.md) for host90 offline city search, presentation flags, behavior, source attribution and verification boundaries.

Offline speech is host-provided: use the existing `speech`/`speech_settings` components and `speech.output` capability. English neural voices and the original pronunciation path are included in the single OnKlav host; do not bundle another engine/model or invoke Android's TTS settings. See sdk/README.md and docs/NEURAL-SPEECH.md for lifecycle, approximate position feedback, native build checks and device-qualification limits.

## Game music

Create or source your own tracks and bundle them with your game. The SDK handles playback, looping and the Music bus. See [Game music](games/GAME-MUSIC.md).
