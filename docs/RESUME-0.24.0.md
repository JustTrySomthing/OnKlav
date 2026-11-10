# Resume 0.24.0 — Settings and Appearance UI cleanup

Source-only 0.24.0-dev/code31. No APK or Android ABI rebuild. Last verified APK
still 0.14.0. Signed app packages are unchanged (Walkie v7); no app APKs.

Read SETTINGS-PORT.md for the entry-by-entry UI and service ledger. Old duplicate
and unreachable Settings placeholder branches were removed. New host screens
are SettingsPreferences, SettingsMessaging, SettingsStorage and SettingsBackup;
SpamPolicy supplies warning-only local screening used by Messages.
Rust SDK adds trusted host walkie_preferences using the app's existing private
store and explicit storage grant. Rust messaging adds bounded checked metadata
backup export/check/merge; no sends/media/permissions restore or reminder replay.

The original dialog/grid styling is retained. Quiet Hours restores the compact
From/Until and staged Repeat flow; narrow/large-text shortcuts adapt correctly.
Shared dialogs respect the actual viewport/keyboard with reachable footer actions.
Appearance keeps staged Apply/Cancel and handles picker launch failure, busy
navigation and background short-screen sizing. Read Aloud lists offline voices
and stops previews on background/disposal. Settings game timeout matches original
30–60 seconds in 5-second increments, saved for the unfinished game ports.

Do not mark all Settings services or full pixel parity complete. Google Drive,
full SMS/MMS history backup, automatic message previews, original speech voice
packs, profile sharing and external game installation are still unfinished;
controls are explicit about this. No carrier gate/default role was enabled.
Walkie phone/background/recovery acceptance remains as described in 0.23.0.

Validation: see source-verification.json; no emulator retry loop. Use restored
env.sh and /root/.cargo/env. Rust changes: build_native.py --host-only before
check_android.py --tests-only. SDK packages did not change; do not regenerate
publisher/signing keys. The future requested phone build must rebuild both Android
Rust ABIs before assembling/verifying the one host APK. Save source/SDK/TODO;
keep publisher/Android signing backups private and preserve Library identities.

Final current verification: 86 Rust +61 Kotlin tests pass (147), zero failures.
Full app/shared host invocation passed; runtime classes match current compiled Kotlin.
No APK/mobile ABI build, real carrier/voice/account or phone/pixel acceptance.
Settings UI implementation pass complete; remaining services are explicitly tracked.
