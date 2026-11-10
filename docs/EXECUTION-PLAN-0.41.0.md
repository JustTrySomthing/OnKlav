# Authorized continuation — Messenger, Groups, Walkie and existing apps

Charles authorized all four on 22 October 2026. Continue across items without
asking to start another app. Preserve OnKlav's single APK and signed internal SDK
packages; preserve the original design. No game/creator-SDK expansion in this pass.

Execution order:
1. Harden message/provider/result recovery and test actual broker failure paths.
2. Extend Groups history/context and provider read-state controls.
3. Finish durable Walkie callback handling and bounded background recovery.
4. Close internal Browser handoff and native app-data/speech lifecycle gaps.

Testing budget: inspect each failure before another attempt; each command has a
hard timeout. Run focused tests for changed behavior, then one integrated check.
If an unrelated host timing failure remains, retain it and isolate it once;
do not repeatedly rerun the complete suite. No emulator boot retries. Build only
one final batch APK. Phone/SIM/carrier/audio/account acceptance cannot be invented;
MMS/default-role stay disabled until the real-phone acceptance requirements pass.
