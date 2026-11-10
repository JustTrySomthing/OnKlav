# OnKlav 0.100.59-rc1 / code 168

Quick ARM64 debug APK correcting the Video Edit duplicate toolbar and undersized portrait preview. The merged Panda reboot/audit remains intact. See VIDEO-FOOTER-REPAIR.md. Package app.onklav.home.prototype.rewrite; original signing identity retained.

APK assembly and standard verification passed: version/package, original signer and v2 signature, ZIP integrity, current SDK packages, game/runtime assets, native/speech bytes, defined classes and 16 KB ELF/ZIP alignment. See apk59-verification.json and evidence/v0.100.59/.

The footer repair passed 14 unique focused UI checks; one ambiguous automation selector was corrected and the affected case rerun successfully. Original and rerun evidence are retained. The prior 58-check merged baseline remains documented in VIDEO-PANDA-MERGE.md. Game and media-engine code is unchanged. No physical-phone/carrier/performance acceptance is implied.
