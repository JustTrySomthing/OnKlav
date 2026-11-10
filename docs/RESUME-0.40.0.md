# OnKlav 0.40.0 — motion and Messenger UI correction

APK: OnKlav-0.40.0-debug.apk  
Version: 0.40.0-dev / code 47  
Package: app.onklav.home.prototype.rewrite  
SHA-256: `44c790a22c5a288aaa6e7960e09d37909062b9dcc8e91f58bc2da865e97c2036`

- Screens and side drawers enter from the right and return to the right, following the latest explicit direction. Outgoing content remains alive through closing instead of being removed immediately. Dialog entrance and dismissal share horizontal eased motion.
- The desktop's stationary dragged icon is fully hidden while the floating icon follows the pointer.
- Message folders have selected styling, matching titles and empty states. Both Messages and Groups Trash expose Restore all and Empty trash controls. Restore all operates on all trash, including rows hidden by search. Phone deletion remains gated and locked messages are retained.
- The number popup restores the original 3×4 keypad, outlined formatted number field, Cancel and Start Message actions. Profile and Walkie recipient entry share phone formatting; canonical dialable numbers remain separate from display punctuation.
- Contacts restore wallpaper, alphabet dividers, circular photos/initials, number summaries, original Message/Walkie buttons and multiple-number selection. The Walkie shortcut selects a recipient in the existing internal SDK app, without automatically transmitting an invitation.
- The purple vector launcher/splash and full-width system/menu backgrounds from 0.39 are retained.

Verification: 135 current Kotlin/framework-host tests passed with no final failures or skips, plus 141 unchanged Rust checks whose source and native-library hashes remain verified (276 total). Focused tests cover intermediate right-side positions and retained exit content, keypad formatting/backspace/target, contact grouping and both shortcuts, real provider-folder filtering and Rust-backed restore, phone cursor mapping, and SDK close/reopen. APK version/signature/ZIP/16 KB alignment, both Android ABIs, current compiled classes and signed internal app packages are verified.

No installed-APK/handset pixel or frame-time acceptance in this environment. Full migration, SMS/MMS/Groups/Walkie carrier acceptance and remaining game/service work are still open. MMS/default SMS role stay disabled. International numbers and short codes are preserved rather than forced into a US pattern.

OnKlav remains the only APK. There is no authored Java application source. Kotlin/Android still uses upstream JVM/framework binaries. This is a development correction, not a declaration that the entire migration is complete. Initial failed checks and subsequent fixes are retained under docs/evidence/v0.40.0.
