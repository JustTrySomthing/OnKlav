# Distributing an app or SDK snapshot

Authors distribute signed `.onklav-a` files, not independent Android APKs. Keep
publisher keys outside source and app folders, back them up privately, retain
the same app ID/key for updates and increase integer package versions. Review
the declared capabilities with users; declarations do not grant permissions.

The starter now includes the existing [Rust dependency notices](../../docs/licenses/rust).
These notices describe third-party dependencies and are not a license grant for
OnKlav's own platform/SDK source. This review does not add or change owner license
terms. An owner-approved SDK use/redistribution license and public distribution
channel still need to be established before calling this a public production SDK.

The current host APK retains its existing development signing identity. Store
signing/account/project and physical-device qualifications remain separate. Do
not advertise independent security certification or native service acceptance
based only on a successful desktop scenario. See [readiness](READINESS.md).

## Reproduce and check this starter

Run `python3 developer/package.py ../OnKlav-App-SDK-Starter.zip` from the platform or extracted kit. Existing outputs are preserved; choose a new filename for a new snapshot. Run `python3 developer/package.py --verify ../OnKlav-App-SDK-Starter.zip` to check the bounded entry inventory and every content hash. CONTENTS.json detects bundle corruption; it is not a substitute for trusting the download source or an app's Ed25519 package signature.

The standalone archive includes the authoring tools, all templates/examples, production broker/packer source, lockfiles and transitive local documentation links. It excludes build directories, APKs, private-key files and native binaries. A Rust compiler/dependency cache is deliberately not bundled. The current target and qualification boundaries are in CURRENT-HOST.md.
