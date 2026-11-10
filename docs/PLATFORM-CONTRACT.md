# OnKlav package and runtime contract

Charles's October 21 clarification is authoritative. Only OnKlav is an Android
application. SDK apps and games are installed, updated, launched and removed by
OnKlav. First-party custom apps/games must use the same SDK package model as
third-party content; host-compiled built-ins are a transitional step. They do
not have Android application IDs, launcher activities, APKs or
Android permission prompts. Themes are installed through Appearance.

| Content | Package | Management location | Runtime |
| --- | --- | --- | --- |
| Apps | `.onklav-a` | Settings > Apps | OnKlav app SDK runtime |
| Games | `.onklav-g` | Settings > Games | OnKlav-controlled Godot/GDScript runtime |
| Themes | `.onklav-t` (provisional spelling) | Settings > Appearance | Validated theme data/assets |

Charles said `.onflav-t` for themes; the canonical spelling is not finalized.
File extensions alone do not establish format validity, trust or permissions.
Original `.icapp`, `.icgame` and `.ictheme` installers are behavioral references;
their Java implementation and old game engine must not be embedded in this port.

## Host boundary

Only the host talks to Android. A package asks OnKlav for capabilities; OnKlav
checks its identity, declared scope and user approval before each privileged
operation. Android permissions are requested by OnKlav when needed. Internal
approval can be narrower than the host's Android permission. Revocation must
stop subsequent operations, streams and subscriptions. Package code must not
receive the Android Context, arbitrary filesystem paths, JNI/native loading,
Java/reflection access or an unrestricted network/OS API.

The SDK must expose useful file, media, network and platform services through
bounded host handles. Each installed package needs app-scoped persistent data,
explicit shared/export/import access, resource limits, lifecycle cleanup and
upgrade/removal rules. Package identity, publisher signatures, schema/runtime
versions and requested capabilities must be reviewed before installation. An
update cannot silently gain new permissions or replace another author's package.

## Current implementation versus required runtime

Implemented in this correction: `android:app` is the only application Gradle
module. Tools and Godot are library modules included in that APK. Tools open by
internal Compose navigation. The trusted bundled engine exercises open a
non-exported host Activity in `:engine`. The export provider is non-exported and
uses the host's authority. There is one launcher entry and no package-install
permission or separate-app launch lookup.

These Kotlin built-ins are trusted host code and a transitional consolidation,
not completed SDK package ports or examples of sandboxed SDK packages. The engine process shares OnKlav's UID and Android permissions; it
provides lifecycle/crash separation, not a security sandbox. Standard Godot
GDScript/PCK loading permits OS/file/network APIs and is insufficient for the
promised permission boundary. Do not enable arbitrary external PCK execution.
A restricted runtime/engine integration and tested broker are required first.
The initial app SDK uses Rust compiled to Wasm, interpreted with imports disabled,
fuel/memory/output limits and a validated declarative UI rendered by Kotlin/Compose.
Notes and a separately compiled Counter developer example use the same signed
package/runtime and installer. Settings > Apps installs, reviews, updates,
exports and removes packages and changes permissions. A Rust broker checks
per-app live grants, publisher identity, session validity and scoped storage.
See sdk/README.md for exact limits and APPS-PORT.md for the seven apps still pending.

The older generic archive-inspection result remains non-executable and unsigned;
it is not the new SDK install operation. Game inspection is still not permission
to run external PCK code. Settings > Games remains a trusted-builtin manager;
external game execution and the game SDK are still unfinished.

## Acceptance requirements for the next SDK chunk

Prove package validation, publisher/update identity, bounded staging and atomic
install/rollback before executing external code. Demonstrate two app packages and
two Godot game packages installed internally without Android installs. Prove
that denied/revoked capabilities, path traversal, direct engine OS access and
resource abuse cannot escape the package boundary. Preserve original manager
and popup behavior. The two app proofs and app capability/resource tests are implemented in 0.10.0.
Two external Godot game packages and their enforced boundary remain pending.
