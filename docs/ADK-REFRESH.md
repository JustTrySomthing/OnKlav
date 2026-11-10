# ADK 0.4.1 refresh

Authoring tools and active guides now target the existing signed OnKlav0.100.42/APK151 snapshot. The native guest protocol is intentionally96; generated min_host96 is correct, and it is not the APK update code. The reusable UI crate0.4.0, ABI helper0.1.0, guest schema/runtime and production Rust sources are unchanged.

Fixed capability listing crashing on four missing media/library entries. Added current-snapshot discovery with explicit baseline/preview qualification, corrected outdated account/storage/UI guidance and removed obsolete reference-host claims. Added portable smoke checks and a standalone source packager/integrity verifier, with build/key/native-binary exclusions and documentation-link closure. CLI output remains UTF-8 and machine-readable, and Windows timeouts attempt process-tree cleanup.

50 unittest methods passed, including a real14-template compile/sign/install/scenario matrix. Independent author, upgrade/publisher/grant isolation, portable project moves, packaging, contract links, deadlines and HTTP policy are covered. The independently extracted starter passed doctor and a generated portable form's real production-broker save/reopen workflow. Four scenario assertions are not counted as four new tests. See adk-refresh-verification.json and evidence/adk-refresh.

Linux was verified with Rust1.90.0 and Python3.12. Native Windows/macOS execution and real device/provider/carrier acceptance remain unqualified; instructions and a reusable smoke runner are included. Existing owner licensing/redistribution terms remain unchanged. No Android APK was built and no privileged guest APIs were invented. Native/SDK artifacts remain source-matched.
