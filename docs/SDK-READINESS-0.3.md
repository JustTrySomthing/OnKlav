# OnKlav0.69.0 / App SDK0.3

October24,2026. Android build77, target36, ARM64. Developer Preview.

The disconnected workspace had been pruned. This release was recovered from the
last saved source checkpoint and completed without restarting broad platform or
game tests. A desktop-runtime hang risk was fixed: requests now have a30-second
response deadline covering blocked writes, and stalled processes are killed.
Source fingerprints invalidate old local core/harness outputs.

## Delivered

- `onklav-ui`0.3.0 shared screen/widget/dialog/drawer builders, private editor and
  preferences controllers, and explicit storage/clipboard/share/contact helpers.
- Public reference controllers derived from all9 included app implementations;
  source parity checks catch drift.12 independent project templates use their
  own app IDs, keys and declared capabilities.
- Shared folder button/browser/search/pagination/cancellation and opaque file
  selection; existing native image/video/audio viewers and speech components.
- Host-owned external Back/title/actions toolbar, overflow, clipping and insets.
  The host reserves Close first and can cancel guest modals/drawers that refuse
  dismissal. Platform identities are reserved against unrelated publishers.
- CLI signed builds with startup validation, versioned filenames, `check`,
  scenarios, and current component/API/compatibility/AI-authoring guides.

Included app packages and verified native/game artifacts retain their existing
bytes and identities. No production core change or native rebuild is claimed.
The interrupted unsaved custom desktop-icon/min_host/blob-cleanup work was not
recoverable and is not shipped as though it had survived.

## Verification

78 named cases passed,0 failed:39 SDK runtime,9 independent author workflow,
4 contract/source/link,2 GUI,1 deadline,11 affected Android app and12 shared
Android cases. Shared checks reused in the second selected invocation are counted
only once. All12 template builds/startups passed, with additional document and
settings persistence/draft cancellation and Files/viewer browsing assertions.
Native checks include320dp layout,80 actions, refused modal/drawer cancellation,
protected Close and reserved-ID rejection.

The starter ZIP was extracted independently on Linux; doctor, generation, key
creation, locked build/signing, package startup check and4 persistence scenario
steps passed. The toolchain and initial dependency downloads are prerequisites,
not bundled offline compiler packages. Native provider replies were not simulated.

One optimized APK assembly completed in195.84seconds and verification in1.82seconds.
Signature continuity, package/version/target, ZIP integrity, packaged signed apps,
Godot JNI retention and16KB native/ZIP alignment passed. Vital lint passed during
assembly. The historical full-lint baseline is0 errors/143 warnings; this is not a
claim of a fresh zero-warning audit. No physical phone was attached.

APK SHA256: `1faaa191adbb551ad0b959c0fdb14728194949cbb17be77239029f2e1892e46d`.
Existing Android signing identity and `app.onklav.home.prototype.rewrite` retained.
The package is sideloadable; release/store qualification remains open.

## Remaining work

1. External cellular Walkie requires safe per-app routing/session ownership;
   current carrier authorization remains built-in-only. Local voice/media is public.
2. Custom desktop icons, signed minimum-host metadata and safe old-package cleanup
   remain future runtime work. Existing external tiles use the host icon; unknown
   manifest fields fail closed. The current pinned-ID limit is100.
3. Physical handset/media/speech/providers/keyboard/accessibility/OEM/notification,
   two-phone/carrier and measured performance acceptance remain open.
4. Independent security review, Windows/macOS setup qualification, Google account
   project configuration and release/store signing qualification remain open.

The shared GUI/service surface is useful for outside app authors, but not every
privileged built-in operation is public. See developer/docs/COMPONENTS.md and
READINESS.md in the SDK. Full evidence: docs/evidence/v0.69.0 in the source archive.
