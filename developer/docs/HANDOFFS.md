# Typed Android handoffs (working source)

Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
Use `onklav_ui::handoff::{handlers,open,share,text}`. These helpers need a
source-matched working host and an explicit `apps.handoff` grant. They do not
add an APK or a separate app runtime. Existing `share.text`, file exports and
host-owned internal app routes remain compatible.

| Service | Input | Additional grants |
| --- | --- | --- |
| `handoff.handlers` | `operation:view/share`, exact `mime` | None |
| `handoff.file` | `operation:view/share`, private blob `id` | `storage.private`, `files.export` |
| `handoff.text` | `text` (1–50000 UTF-16 units) | `share.text` |

All requests require a direct user action. Discovery returns only
`{available:boolean}` for the selected type/action, not package names, paths,
URIs or an installed-app inventory. MIME wildcards, arbitrary actions, target
packages, caller-supplied owner/URI/path and extra arguments are rejected.

The host resolves the private immutable blob under the current signed
app/publisher/session and displays its name/type for review. Cancel keeps the
private file untouched. Continue copies at most20MiB into a separate private
handoff cache and opens Android's chooser with one read-only content URI and
ClipData. Text uses the normal text/plain share chooser after host review.
A result `{handed_off:true,completed:false}` means only that the chooser opened;
it cannot tell whether the receiver opened/saved/sent anything or the user cancelled.

File handoffs are pinned to the installed signed package hash, grants and a
durable revocation epoch. Epoch invalidation commits before file cleanup, so a
failed deletion followed by regrant cannot resurrect an older URI. New
provider opens/metadata requests recheck that owner and expire after10 minutes.
Update, revocation and removal revoke grants/delete only that app's handoff
copies. At most4 copies per app,16 total and40MiB total are retained. Failures
before launch discard staging. Paths are canonical and confined to the owning
app's private blob directory; symlinks and malformed provider routes fail closed.
No write/persistable/prefix URI grants are issued. A receiver can retain bytes
or a descriptor already obtained during a permitted share; revocation cannot
recall an already disclosed copy.

This discovery API covers compatible Android activities. It does not register
arbitrary SDK packages as internal intent handlers; existing explicit internal
host routes still govern those app-to-app flows. Generic signed internal-handler
registration/routing is a separate extension, not claimed by these checks.

Production path: public Rust helper → Rust strict request/grant/token broker →
Compose review using the shared centered dialog → Kotlin native private-file
resolver/staging → Android chooser/provider. Cleanup uses the existing AppSdk
update/revocation and durable removal paths. No guest file paths are returned.

Focused Rust checks use actual signed packages to exercise independent owners,
foreign handles/tokens, publisher continuity, retained-data reinstall, removal,
missing grants and host-owned resolve. Isolated Android tests compile production
services/UI against real shared components, and inject registry/authorization
fixtures explicitly. They do not qualify full-host JNI, a real Android chooser,
OS URI grant enforcement or receiver/OEM behavior. Those acceptance gates remain
open for actual devices/providers. Earlier missing-dependency observations are historical, not a current toolkit installation requirement.
