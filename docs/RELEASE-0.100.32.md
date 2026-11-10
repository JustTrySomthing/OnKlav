# OnKlav 0.100.32-rc1 / code 141

Fast unshrunk ARM64 debug build, explicitly requested to avoid spending time on
APK size optimization. Same package and original signer; install as an update.
This build is debuggable and larger than the preceding optimized APK. No new
signing identity, native library, SDK package, voice bank or game rebuild.

Write's white-paper native editor now has a dedicated light Android theme with
dark popup action/text colors. This addresses unreadable spelling suggestions,
Add to Dictionary and Delete actions when the surrounding OnKlav theme is dark.
Native spelling, selection, formatting and keyboard behavior remain enabled.
Compress's Files & folders card now says Lossless ZIP, without archive.

Includes all completed source work since 0.100.31: Calendar, GMX/private-domain
Email setup, Phone and default-dialer setup, speed dial/contact/voicemail polish,
Phone/Camera/Apps/Messages/Groups main bar, Settings/Games app icons, named desktop
folders with Utilities/Create defaults, first-page Apps reset, and Cloud Files
with Google Drive UI retirement and provider-neutral Backups.

One focused Android 35 host regression verifies actual editor-context popup
contrast and autocorrect retention, plus the Compress label. Assembly and APK
verification passed: identity/version/signature, ZIP integrity, 16 KB alignment,
source-matching native/SDK/speech/game payloads, and current feature classes.
Earlier focused feature checks/full lint are preserved and were not repeated.
No broad testing loop or additional shrinking/tuning was performed. See
apk32-verification.json and docs/evidence/v0.100.32.

Real-phone/OEM popup appearance, call/carrier behavior and live cloud provider
qualification remain separate; this is an owner testing APK, not store certification.
