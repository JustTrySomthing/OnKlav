# App developer SDK review — OnKlav0.70 / SDK0.4

October24,2026. Apps only. Reference Android host0.70.0-rc1/build78, target36.

The review found and fixed a structural problem: included apps and developer
examples maintained separate controller copies. All9 included apps now link the
same public onklav-ui0.4.0 crate exposed to developers. Their entry points contain
only the app title/controller binding and ABI export. The existing publisher key
and app IDs were preserved, and package versions were increased for updates.

## What developers can reuse

| Area | Available now |
| --- | --- |
| Application structure | Host Back/title,2 visible actions, overflow with secondary actions, protected Close, clipping and system/keyboard insets |
| Appearance and layout | User theme/font family, accessible native controls, centered dialog scale/fade, sliding pages/drawers, wrapped narrow groups, scrolling long forms |
| Widgets | All20 current runtime kinds catalogued with public helpers: text, fields/editors, actions/icons/records, toggles/choices, speech/settings, web/media, drawing/image/width/palette and local voice |
| Persisted forms | Declarative fields/defaults/required validation, stable IDs, private JSON persistence, Save/error/draft recovery and explicit reset for unreadable data |
| Collections | Search,40-row pages, empty states, New/detail/editor, Save, Back/draft prompts and confirmed deletion |
| Custom navigation | Bounded serializable route history and native Back binding |
| Files | Full included Files controller; shared folder action, chooser/search/Up/pages/cancel and opaque selection IDs |
| Media and speech | Existing native image/video/audio viewer and installed TTS engines/voice controls; no replacement player or URI plumbing |
| Web/feed/drawing/local voice | Actual included Browser/RSS/Reader/YouTube/Annotation/Walkie controller code and existing broker/native components |
| Private services | Shared record, clipboard, contact, export/share helpers; explicit user grants and live revocation |
| App identity and icons | Author signing, publisher/version pinning, desktop install/update/removal, signed minimum host and themed stock or bounded PNG icons |
| Developer workflow |14 templates, portable frozen dependencies, catalog search, key-free local verify, safe dependency upgrade with backup/rollback, locked shipping builds and scenarios |
| Documentation | Rewritten quickstart, component/API/compatibility/testing/AI-authoring guides, machine contract/catalog and3 runnable cookbook projects |

The included modules are Files, Notes, RSS, Read Aloud, Browser, YouTube, Annotation,
Social and Walkie. Social remains its intentional Hello World placeholder. The
shared API does not copy first-party identities, grant permissions automatically
or expose privileged host methods to Wasm. Built-in content adapters for existing
Notes/Files data, trusted Walkie root-panel presentation and carrier/message
handoffs remain explicit host policies. External private storage stays isolated.
Media/web/drawing content retains its colors; platform controls follow appearance.

## Concrete defects addressed

Native toggle/select/chip/record/slider/palette and centered-button controls did
not consistently respect enabled=false. The renderer now does; disabled active
media/web/drawing components show an inactive placeholder. Secondary actions
were lost when header items moved into overflow; those actions are retained.
Full-page toolbar limits and overflow now apply to included apps as well. Long
multi-field forms scroll and grouped controls wrap with screen width/font scale.

Generated projects no longer depend on fragile absolute SDK paths by default.
They vendor the small public source dependencies; a moved project was built and
verified. `verify PROJECT` uses a disposable test signer/root and runs actual
broker scenarios, avoiding permanent key/phone setup during ordinary development.
`upgrade PROJECT` keeps the old vendor/lockfile and restores them after a failed
update. App source, identity, signing key and installed phone apps are untouched.

Optional signed min_host/icon fields are validated. Generated apps require78;
newer-host packages are rejected. PNG input is limited to6144bytes and128×128,
with CRC/chunk/pixel checks in tooling and safe chunk/dimension checks before
native IO-thread decoding. The decoder uses2 concurrent slots and a1MiB cache;
trim does not recycle displayed images. Registry data is bounded to2MB/100 pinned
IDs. Bounded old-blob collection runs only after successful registry commits and
preserves current/retained packages, documents, links and unrelated files.

Build/source handshakes reject stale desktop brokers, and blocked runtime writes/
replies and build process groups have finite deadlines. An Android packaging-cache
issue was also caught: listing metadata said78 while the old APK remained77.
The build now removes only that regenerable APK before packaging after resource
changes. Version/signature/native/package verification remains enforced; the
correct rebuilt artifact passed. No source, signer or version was relabelled to
make the old artifact appear current.

## Bounded verification

**247 unique named cases passed;0 failed:**189 full Rust core,6 shared GUI,
15 build planner,7 developer-tool,2 deadline,9 independent-author workflow,
5 published-contract/source/link and14 affected Android app cases. Repeated
runs and scenario assertions are not added to unique counts. The12 shared Android
baseline cases were not freshly rerun and are not added again.

All14 template controllers were compiled/installed/launched and their declared
scenarios exercised during this pass. The final common metadata/tool changes were
also exercised through the independent-author suite, icon/minimum preflight and
all3 cookbook projects. Those projects check form/list persistence and keeping a
draft while opening/cancelling shared file browsing. A clean starter extraction
passed doctor, portable form generation and key-free verify with4 persistence
assertions. Native/provider examples with empty scenarios are startup checks only.

Android cases cover320dp chrome/overflow/secondary actions/disabled controls,
refused guest cancellation/Close, reserved IDs, a long form at160% system font
scale, real desktop custom-icon rendering, decoder/cache trim bounds and minimum
host rejection, plus existing Notes install/save/clipboard/remove/reinstall,
appearance palettes and popup/header layout. Native PNG mapper visibility was
fixed after compilation. The icon UI assertion was corrected to query the
unmerged accessibility tree after inspecting the actual tree; the rendered icon
was present. Only the affected failed case was rerun after diagnosis. Raw XMLs
are preserved unchanged; a per-case JSON index selects actual final results.

Linux and ARM64 JNI were freshly rebuilt. Core tests took44.3seconds including
build overhead, Linux JNI6.6seconds and Android JNI74.9seconds. The final APK
packaging pass reused compiled outputs and finished in18.51seconds, then passed
verification in2.02seconds. Vital lint passed. Historical full lint is0 errors/
143 warnings; no new zero-warning/full-lint claim is made. No physical phone was
attached. Evidence: docs/evidence/v0.70.0 and source-verification.json.

## What remains before a broad public production SDK

- External cellular Walkie needs per-app incoming routing/session ownership and
  real carrier acceptance; current carrier authorization stays built-in-only.
- Native camera/date/time pickers, secure credential storage, arbitrary custom
  widgets and third-party background notifications/services need explicit bounded
  host APIs. They cannot be added safely by allowing raw Android imports.
- Physical media/speech/providers/keyboard/accessibility/OEM/notifications/carrier
  and measured multi-device performance remain unqualified. Windows/macOS setup
  has not been freshly tested. Independent security review remains unperformed.
- Owner-approved SDK use/redistribution terms and a public distribution channel,
  Google account/project configuration and release/store signing/account
  qualification remain open. Existing Rust dependency notices are included;
  they are not an invented license for OnKlav's own code.

The kit now removes much common app scaffolding and uses one public app system.
No independent comparative study establishes that it is the easiest platform
available, and it is not a full arbitrary OS SDK or store-certified release.
