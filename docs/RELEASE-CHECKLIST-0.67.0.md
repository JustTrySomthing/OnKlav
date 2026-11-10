# OnKlav 0.67.0-rc1 release candidate

Code75, Android target36, ARM64. Games remain unchanged and deferred. Social
remains the intentional Hello World app. This is an upgrade candidate using the
existing signer and package identity, not a certified finished store release.

## Changes

- All Material color roles follow the selected light/dark palette. Dialogs,
  popup containers, secondary/tertiary controls and inverse colors no longer
  inherit unrelated default accents. Buttons use contrast-safe ink. Native
  date/time pickers follow the selected mode, accent and font.
- Settings, Profile, Setup and Drive content has bounded tablet widths. Labels
  wrap, dialog actions can scroll, narrow SDK toolbars retain touch targets,
  short-height editors scroll and recording controls shrink to available width.
- Image headers are validated before allocating full bitmaps. Background and
  message preview decoders have bounded concurrency. Memory-pressure callbacks
  release caches without recycling displayed bitmaps or cancelling active
  visible thumbnails. Replacement image loads clear stale images.
- Export, file-open and message-transfer providers reject linked files/folders
  and enforce canonical containment. Failed export writes clean their own
  partial output. Automatic OS backup/device-transfer rules explicitly exclude private app data.
  Existing capability, signing, HTTP/DNS and receiver/service
  boundaries remain enabled.
- Removed unused legacy Reader, RSS and generic tool screens and their obsolete
  dependency. The Notes recovery screen remains because real data adoption uses
  it. Missing app packages show a safe recovery page.
- Removed obsolete user-facing migration/transport notes. Walkie package11 uses
  an accurate local-clip status and retains its publisher identity. Restored selected recipient name/number in Walkie and added Contacts
  section headings for accessibility. Corrected outdated host-test routes and bounded scrolling in paused test windows.

## Evidence

Current counts and exact logs are recorded in source-verification.json and APK
signature/content/alignment checks in apk-verification-optimized.json. Results
from the lost workspace are not counted as fresh evidence. Rust, Python and
Android checks are separate from installed-device and carrier acceptance.

## Unclosed release qualifications

The existing development certificate is retained for upgrade continuity. Store
release/upload signing, AAB delivery/account and permission declarations are not
configured here. Replacing the certificate would break existing upgrades.
Google OAuth registration for the actual package/certificate plus Drive API
configuration requires the real Google project/account, which was unavailable.
MMS release acceptance remains false; carrier trials are explicitly enabled by
the user. Actual SMS/MMS, dual SIM, two-phone Walkie, provider restore,
notification/Doze, speech engine, WebView/media, rotation and OEM/accessibility
acceptance remain unverified on physical devices. No attached device is available
for measured startup/frame/memory performance. Host layout checks cover normal
phone, 320dp large text, tablet and landscape; they do not certify every device.
Independent SDK/public API security qualification remains separate. No claim of
zero bugs, universal responsiveness or store certification is made.

## Final automated review

451 unique fresh checks pass:187 Rust,231 app Android,12 shared Android and21
Python. One broad Android pass plus affected regressions; games were not rerun
or modified. The four settings snapshots were inspected after layout assertions.
Lint finishes successfully with zero errors,143 warnings and2 informational
suggestions. Full diagnostics are retained in docs/evidence/v0.67.0.

The remaining warnings are primarily109 KTX style suggestions,9 available
library updates and9 WebView feature-guard diagnostics. The WebView paths are
already protected by runtime feature checks and tested permission/profile gates;
lint does not infer those helper/check contracts. Other warnings include the
intentional ARM64-only phone build, API29 compatibility/EXIF APIs, conservative
free-space checks, durable preference commits and a factory-only custom WebView
constructor. These do not certify all dependency/device behavior and are not
hidden by a global lint baseline or disabled error checks. Store/account/device
qualifications above remain unclosed.

Automatic backup configuration follows the Android backup XML contract:
https://developer.android.com/identity/data/autobackup

## APK delivery

`OnKlav-0.67.0.apk` is an optimized, non-debuggable ARM64 build for Android10+;
version0.67.0-rc1/code75,target36. Signature continuity, native/SDK/game source
freshness, package contents, private-backup policy and16KB ZIP/ELF alignment
checks pass. No old APK was relabelled; one current APK was assembled.

Bytes: 39254028

SHA256: `d61696947740e61f33cbe785afdc4a9ba7d5e967f41d09f7e9723a0d30f5d52b`
