# Social — intentional SDK placeholder
This document records the former placeholder. The current app is described in [Real Social](REAL-SOCIAL.md).

Charles clarified that the original Social app was only a placeholder and asked
for a minimal Rust Hello World package, with no social feed or extra features.
This explicitly supersedes earlier speculative descriptions of a complete
Social/communications port as required work.

`sdk/apps/social/src/lib.rs` is the complete app controller. It uses the standard
onklav-app Rust ABI and returns a Social frame with one Hello World label. Its
manifest declares no capabilities; there is no storage, networking, identity,
feed, fake data or host effect. The retained publisher signs Social.onklav-a.
The existing desktop icon maps to the normal AppSdk/SdkAppScreen route. The host
provides its shared header/Back control. No separate APK is produced.

The actual package test installs the signed package with zero grants, executes
its Wasm, verifies its only label/no effects/no action buttons, then removes it.
The Compose host test opens it from the Social desktop icon, checks Hello World
and returns with Back. See source-verification.json for current results. APK and
handset tests are deferred under the current user-requested workflow.

There is intentionally no Social functionality to finish beyond this placeholder.
Adding social features later requires a new user request.
