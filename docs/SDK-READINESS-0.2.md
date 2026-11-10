# OnKlav App SDK0.2 readiness report

2026-10-24, America/Chicago. Reference platform:OnKlav0.68.0-rc1/code76.

The app runtime passed its focused readiness gate, and the outside-developer
starter now has a verified authoring workflow. SDK0.2 is a **Developer Preview**
for the documented app surface. It is not game tooling, an independently audited
security release, or a claim that every native/device feature is qualified.

## Platform verified first

Fresh production Rust SDK coverage:39 unique passing cases after correcting one
obsolete test-only status assertion. Signing/publisher pinning, tamper/version/
review checks, imports/fuel/memory/frame bounds, app-private isolation, live grants,
pending token invalidation, update retention and removal/reinstall are exercised.
The actual runtime code before the cfg(test) module is byte-identical to the
source associated with saved source version74. Existing native artifact hashes
are retained with that explicit source reconciliation; no new mobile build or
APK is claimed. Android production source/UI and games are unchanged.

## Developer gaps addressed

- Project generator and a private-document template with a locked dependency file,
  scenario, themed controls and ordinary new/refresh/save actions.
- CLI for prerequisite checks, publisher key creation, locked Wasm compilation,
  signed packaging, actual-runtime inspection and scenario tests.
- A desktop JSON-line harness calls the exact production Rust SDK broker using
  the same locked runtime dependency versions. No fake guest interpreter.
- Independent-author acceptance covers a new publisher, app creation/signing,
  denied/revoked grants, persistence/restart, upgrades/downgrades, publisher
  protection, cross-app isolation, tamper/review checks and remove/reinstall.
- Current API/widget/service/capability/limits reference, compatibility policy,
  troubleshooting, AI authoring instructions and machine-readable inventory.
- Historical SDK chronology is clearly archived; the current SDK README routes
  developers to current instructions. Advanced component scope is explicit.
- Starter packaging includes developer tools/docs/templates, core/packer source,
  Rust helper, examples, lockfiles and required reference guides, with a root README.

The test-tool process streams now close correctly. Existing projects and release
outputs are protected from accidental overwrite; author keys stay outside source.
The toolkit requires Python3.11+, Rust1.90+, Wasm target and a native linker.
It does not require Android Studio, Gradle, a JDK/NDK or OnKlav's private keys.
The first build still needs access to Cargo dependencies; this is not an offline
compiler distribution.

## Completed verification

51 unique focused checks pass:39 runtime,9 developer-workflow and3 contract/content.
The final workflow run treats ResourceWarnings as errors. Initial failure and
correction evidence is retained, not deleted or counted as additional cases.

The starter was also extracted to a separate directory and used through its own
`doctor`, `new`, `keygen`, `build`, `inspect` and `test` commands. It created and
signed a distinct app ID using its own disposable author key. The four-step
save/reopen scenario passed. These extra steps and repeated content checks are
not added to the51 count. The final template's icon hints are separately compiled
and checked through its four-step scenario.

Evidence:docs/evidence/sdk-readiness. No Android/game/emulator audit loop or APK
assembly was repeated for this authoring/docs release.

## Remaining qualifications

- Physical Android/Compose layout, input/accessibility, lifecycle/OEM, permissions,
  pickers, WebView/media/profile cleanup, speech, notifications and measured
  performance are not established by headless tests.
- Live cellular/MMS and two-phone Walkie acceptance remain open; advanced native
  components are documented as development preview.
- Independent platform security review is still unperformed. Self-review and
  boundary tests do not substitute for it.
- Linux clean extraction is tested; a fresh Windows/macOS setup is not claimed.
- Full game development, arbitrary native Android APIs, a stable arbitrary UI
  toolkit, custom app assets and automatic package-blob garbage collection are
  not implemented by this SDK release. These are explicit scope constraints.

Outside app authors can use the developer preview now within the documented
surface. Keep version/signing discipline and test native interactions on a phone
before distributing an app as production-ready.
