# Current host and API status — ADK 0.4.1

This page is the current target for the developer kit. Historical phase reports describe earlier snapshots, not the installed host's current inventory.

| Identifier | Current value | Meaning |
| --- | --- | --- |
| Developer tools/ADK | 0.4.1 Developer Preview | Authoring, packaging, discovery and guides |
| Reference Android host | 0.100.42-quick / APK code151 | The packaged phone update |
| Guest host protocol | 96 | Compared with a signed manifest's min_host |
| Package schema | onklav.app.v1 | Signed package structure |
| Guest runtime | rust-wasm-ui.v1 | Rust/Wasm ABI |
| UI crate | onklav-ui 0.4.0 | Source-compatible reusable controllers |
| Low-level crate | onklav-app 0.1.0 | ABI and effect helpers |

Do not set min_host to151: APK update codes are independent of protocol compatibility. Generated projects use96. All crates/lockfiles remain at their real source versions; changing documentation does not require rebuilding or relabelling a phone binary.

## Discover features without confusing implementation with qualification

`python3 developer/onklav.py capabilities` defaults to `--channel current`, describing implementations included in this exact host snapshot. It reports the APK and protocol separately. Each item retains its baseline/preview channel and qualification note. The legacy `released` channel is the earlier stable subset; `working` remains a compatibility alias for implemented source inventory. None of these commands probes a phone, grants permissions, or certifies provider/hardware success.

- Document/form/list storage, signing, package upgrades, themed UI, Files, speech, web/media and local voice have public components and runnable examples.
- Camera still capture, secure secrets, notifications/jobs, bounded HTTPS/accounts, calendar and scoped device/handoff services are implemented preview APIs. Their exact grants, user actions, quotas and Android requirements apply. Test them on the actual target device/provider before distribution.
- External cellular Walkie routing is present in source, but real two-phone delivery/reconnect acceptance remains open. Do not advertise it as qualified voice calling.
- Guests do not get Android Context, arbitrary native views/widgets, filesystem paths, raw OS/network imports, install/grant powers or general Android background services. These are deliberate platform boundaries, not permissions unlocked by a host APK manifest.

## Current host conventions

The host owns the rounded X dismissal control, slide animations, theme/fonts, save prompts, safe areas and accessibility. Use in-app routes or explicit parent controls for navigation; do not duplicate the host close button or override it with a guest-only faux toolbar. System Back can navigate internal history.

Use the host Files effects/selectors. Current phone selection uses OnKlav/Android tabs, media/document/download categories and Internal files. Android permission is reviewed during host setup. Old document-tree selections and Photos/APK locations remain compatible. System/app-managed containers may be browsed but must not be renamed, moved or deleted. Guests retain opaque IDs and live grants, never native paths or credentials. Save As keeps an existing file intact; interrupted internal saves remain recoverable.

The native Email app uses Yahoo, Google or private-domain app-password settings. Native Cloud Files uses pCloud/Koofr. These host account stores and native IMAP/SMTP are not automatically guest APIs. The public Mail/JMAP examples are separate optional HTTPS/account-broker examples; they do not represent the built-in Email setup or imply that live OAuth/provider configuration has been qualified. No retired GMX/Drive/4shared setup is required for a new app.

## Validation boundary

Use doctor, verify, check and the packaged ci_smoke.py. Desktop scenarios use the production broker and stop at real OS/provider requests; they never fabricate device success. Native PNG preview needs the Android host toolchain and is optional. Linux verification for this refresh is recorded in ADK-VERIFICATION.json. Windows/macOS commands are supplied, but a Linux run is not a fresh native Windows/macOS acceptance result. SDK-owner redistribution terms and independent security review remain separate from build success.
