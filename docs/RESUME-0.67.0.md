# OnKlav 0.67.0-rc1 — current release candidate

Code75, Android target36. The latest request authorizes one optimized ARM64 APK;
games remain deferred. See RELEASE-CHECKLIST-0.67.0.md, TODO.md and current
source/APK verification reports. Old no-APK instructions are superseded.

The environment reset lost the prior unsaved pass. Source was restored from
canonical0.66 and fixes were reapplied, with a saved recovery checkpoint before
building. Current evidence is fresh: 187 Rust, 231 app Android, 12 shared Android
and21 Python cases,451 unique passing checks. One full Android pass was followed
only by affected Contacts/Walkie/voice/drawing/model cases. An explicit Annotation
host-test dispatcher prevents test-only IO-thread focus recomposition. No lost
workspace or historical results count toward the current total.

All Material roles and native pickers follow appearance. Settings widths,
wrapping, dialog actions, small-height editors and recording controls adapt.
Image decoding is bounded and headers checked before allocation; memory pressure
evicts caches safely. Providers reject linked files/folders. Automatic OS backup
and device transfer explicitly exclude private app data. Removed unused Reader,
RSS and generic tool screens; keep the live Notes data-recovery fallback.
Walkie package12 restores the selected recipient name/number and accurate local
clip status, signed by the existing publisher. No game sources/assets changed.
Current Linux and ARM64 JNI were rebuilt with fresh tests and NDK r27c.

Preserve the existing Android development signer/package for upgrade continuity.
Store release/upload signing,AAB/account/permission declarations, actual Google
OAuth registration/Drive configuration, physical carrier/dual-SIM/two-phone/device
and measured performance acceptance remain unclosed. SDK independent security
qualification is separate. No attached phone was available. Do not describe this
as bug-free, universal device acceptance or a store-certified finished port.

Use scripts/build.py for an APK only when needed. Reuse source-matched SDK,
native and game proofs. Focus tests on changed paths; avoid full-suite/emulator
loops. Keep all private keys outside source and deliverables.
