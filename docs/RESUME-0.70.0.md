# Resume OnKlav0.70.0 / SDK0.4

Read AGENTS.md, TODO.md, developer/README.md, COMPONENTS.md and COOKBOOK.md.
This pass reviews non-game developer ergonomics and unifies included apps with
the public SDK. No game source or feature work was performed.

All9 included app sources are thin wrappers around developer/components;
there are no parallel copied controllers. Public onklav-ui0.4.0 includes shared
forms, collection/list-detail, route history, existing file/media wrappers,
all20 widget helpers, common services and the9 actual controllers. New projects
vendor portable dependencies. CLI catalog/verify reduce discovery/testing setup.
Three runnable cookbook projects have scenarios; native/provider templates retain
startup-only checks and must not be counted as complete OS acceptance.

The Android host0.70/build78 shares full-page chrome/overflow across all apps,
preserves secondary actions, wraps narrow groups and scrolls multi-field forms.
Enabled state is respected by native controls. Root-panel presentation remains
trusted built-in behavior; external pages retain protected chrome and can open
shared dialogs. Internal shared-content adapters and carrier handoffs are explicit
privileges, not public guest APIs.

The core now accepts optional signed min_host and icon metadata, enforces build78,
bounds icon headers/registry size and collects only unreferenced cache packages
after install commits. Both Linux/ARM64 JNI were freshly rebuilt and proven.
PNG preflight checks CRCs, chunk whitelist and bounded pixels; native decoding
checks safe chunks/bounds on IO with a bounded cache. Existing publisher and
Android signing identities remain unchanged;9 included app versions were bumped.

Testing is bounded and evidence is retained under docs/evidence/v0.70.0. Initial
icon mapper visibility was fixed. A desktop-image test originally looked inside
a merged accessibility tree; its unmerged-tree query was corrected after inspecting
actual semantics. Final unique counts and package checks are in the review report.
Do not repeat broad suites or emulator loops. Default APK build: scripts/build.py.
Use --online once only if the dependency cache lacks an artifact. Native changes
require scripts/build_native.py; unchanged artifacts may be reused by hash proof.

Remaining: external carrier routing; bounded APIs for camera/date/time/credential
vault/background notifications/custom widgets; real device/provider/OEM/carrier
acceptance, independent security review and store/account qualification. Never
remove identity/capability gates to make unsupported examples appear to work.
