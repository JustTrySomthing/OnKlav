# Resume OnKlav0.69.0 / SDK0.3

Read AGENTS.md, TODO.md, developer/README.md and developer/docs/COMPONENTS.md.
This checkpoint recovers from a disconnected and subsequently pruned workspace.
The saved0.68/SDK0.2 source was restored, then shared components and host changes
were rebuilt. Do not claim that the unsaved prior icon/min_host/garbage-collection
implementation is present: those runtime changes were not recoverable and are
not part of this release.

The current host adds protected external app toolbar/overflow/Close, native
modal/drawer cancellation, clipped content and generic file-manager refresh.
The SDK supplies12 templates, public included-app controllers, Screen/widgets,
file selection/media wrappers, documented services and a bounded response reader.
Source parity checks bind reference controllers to included app logic. Included
signed packages, mobile/Linux JNI and game pack retain their verified saved
identities/bytes; core production source was not modified in this follow-up.

Current native evidence is11 affected app tests plus12 shared tests; the second
selected invocation reused shared checks.39 SDK runtime cases,9 independent
workflow cases,4 contract cases,2 GUI cases and1 deadline case passed.12 template
build/launch smoke checks include relevant persistence/browsing assertions. See
SDK-READINESS-0.3.md and source-verification.json for final packaging checks.

No attached phone: physical media/speech/provider/carrier/OEM/device and
independent security acceptance remain unclosed. The SDK is a developer preview.
Cellular Walkie remains built-in-only; do not remove the identity gate without
safe incoming routing/session isolation. Custom desktop icons/minimum-host
metadata remain future work. Do not copy first-party signing keys into SDKs.

Keep commands bounded; no repeated broad suite/emulator loops. Default build:
python scripts/build.py (optimized ARM64). The first cold cache may need --online.
For focused checks use scripts/check_android.py --tests-only --test CLASS.
Use --timeout30..600 to set a finite phase budget; --online fetches missing cache
entries. Restore existing Android signer only. Preserve the unchanged native and
signed app proofs. The9 reference controller copies are not live host dependencies;
update them from included sources and retain the parity test when those evolve.
