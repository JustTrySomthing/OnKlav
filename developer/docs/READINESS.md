# ADK 0.4.1 readiness

Current reference: OnKlav 0.100.42-quick/APK151, guest protocol96. UI crate0.4.0 and ABI helper0.1.0 are unchanged. See [current host](CURRENT-HOST.md) and the bundled ADK-VERIFICATION.json for the actual results of this refresh.

The kit includes generation of portable projects, locked Wasm builds, disposable verification keys, author signing, package inspection/checks, the production-broker headless harness, scenarios, dependency upgrade/rollback, component discovery, templates/examples, and a standalone source distribution. The packager follows required source and local documentation dependencies and rejects secrets/build outputs. A content manifest allows checking the extracted bundle.

The current-host discovery channel includes implemented preview services without promoting them to a qualified stable baseline. Guides now distinguish current implementation, guest compatibility level, APK version and device/provider acceptance. Legacy Notes is not an installed desktop app; its reusable controller/template remains available for independent private-document apps and compatibility.

The supported developer surface is suitable for building apps within the documented OnKlav model. It is not an arbitrary Android SDK. Camera/secrets/notifications, network/accounts/device services and carrier functions retain their explicit documented qualification limits. Generic native plugin loading, Android Context, arbitrary guest imports and unrestricted background services remain unsupported by design.

Fresh Linux authoring/packaging verification is recorded in the report. Run `python3 developer/ci_smoke.py` on each actual Windows/macOS development environment as well. Their setup instructions do not claim native acceptance from a Linux run. Public SDK licensing/redistribution terms remain an owner decision; third-party notices are included and are not a new license grant for OnKlav itself. Physical OEM/device/provider, two-phone carrier and independent security qualification remain separate.
