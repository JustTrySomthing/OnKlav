# OnKlav 0.100.34-rc1 / code 143

Fast unshrunk ARM64 debug APK with the original package and signer; install as an
update. Source-matching native, SDK, speech and game payloads were reused.

Includes the requested two-page desktop arrangement from the reference images:
page one Browser/Email/YouTube, Radio/Podcasts/AudioBooks, eBooks/RSS Reader/Games,
Calendar/Clock/Weather, Write/Social/Maps; page two Utilities/Create/Settings,
Files/Cloud Files. Voice Record is in Create. Calc, Vault, Convert, Read Aloud,
Walkie-Talkie, Flashlight and Power join the existing Utilities apps.

Arrangement version 2 applies once to existing installations and preserves later
customization and unrelated custom folders. Icon moves use destination-page gaps
before spilling, including gaps before the drop. Same-page reorder remains on
that page; empty-slot drops leave other icons still. Quick edge releases no
longer jump pages; holding at the edge still switches pages. See
DESKTOP-DEFAULT-LAYOUT.md for details.

23 prebuild host logic/UI checks passed with zero full production/test lint
errors. APK assembly and verification passed: package/version/original signer,
ZIP integrity, 16 KB alignment, native and bundled payload hashes, and current
feature class definitions including DesktopArrangement. Assembly took 46.82s;
verification took 4.23s. No repeated broad tests or shrinking were performed.
See apk34-verification.json and docs/evidence/v0.100.34.

Physical device touch/rotation/OEM acceptance and real carrier/live cloud account
qualification remain separate; this is an owner testing APK.
