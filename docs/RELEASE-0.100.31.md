# OnKlav 0.100.31-rc1 / code 140

Single optimized ARM64 APK with the original package and signer. Install as an update to preserve data.

Includes the latest Email mobile UI/workflow improvements and Gmail/Custom domain setup, Vault incomplete-setup restart without requiring the forgotten password, and sharp clone Stamp with adjustable persisted Hardness. All earlier 0.100.30 features remain included.

A valid password-only Vault is treated as unfinished setup: new password, confirmation and three recovery answers. Complete atomically creates a new empty Vault, then asks for the newly chosen password. Completed Vaults retain password/three-answer access. This is a setup restart, not recovery of password-only content.

Prior focused implementation checks passed: 18 Email and 32 Vault/Stamp checks; they were not rerun for packaging. Real-provider, physical-phone/background/carrier/touch and independent security acceptance remain separate. See apk31-verification.json and evidence/v0.100.31 for actual packaging results.

Optimized assembly, release-blocking lint, package/version/original-signature, ZIP integrity, 16 KB ZIP/native alignment, source-matching payloads and six ViewModel constructor checks passed. First assembly hit its 600-second limit during R8; bounded cached-output resume succeeded. Future optimized assembly now allows 900 seconds.
