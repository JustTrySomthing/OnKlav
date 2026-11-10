# OnKlav 0.100.29-rc1 / code 138

One optimized ARM64 APK, original package and signing certificate. Install as an update to preserve data.

Includes Authentic, Code Edit, Image Edit Retouch, Vault recovery, utility icon/name refinements, and the mobile Video Edit workflow/polish, alongside prior OnKlav features.

Assembly, release lint, signature/package/ZIP/native alignment and source-matching payload verification passed. New app ViewModel constructors survived R8 and the packaged QR license matches source. Existing focused feature checks are documented in their feature reports; they were not rerun for this packaging task. Matching native, SDK, speech and game outputs were reused. The first R8 attempt exceeded the assembly time limit; the cached retry completed successfully.

Physical phone, camera/provider, security and media performance qualification remains pending. See apk29-verification.json and evidence/v0.100.29 for build and packaging evidence.
