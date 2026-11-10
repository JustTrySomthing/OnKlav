# Power and setup — 2026-10-30

Power is an included host system utility in the existing single APK. Its desktop entry uses the power symbol. Opening Power invokes AccessibilityService.GLOBAL_ACTION_POWER_DIALOG once when enabled; its large button opens it again. Android supplies the actual Restart/Power off dialog and confirmations. Service reconnection, settings return and rotation do not automatically open it again. If the service is disconnected, the screen offers retry/access settings and does not claim success.

The exported service is protected by android.permission.BIND_ACCESSIBILITY_SERVICE. It requests no screen content, gestures, event subscriptions, overlay, device administrator or networking. No SDK guest API or unprotected receiver exposes global power actions. This utility is part of the host itself rather than an additional independently signed SDK payload or APK. Existing APK/publisher identities are unchanged. It appears in Apps and can be hidden/shown through the existing app visibility controls.

Setup version 4 introduces actual-state numbered cards, completion counts, a progress bar, a highlighted next step and a persistent contextual primary action. Stages cover default SMS (telephony devices), host runtime access, notifications, precise alarms, lock-screen alarms where applicable and Power accessibility. Completed cards collapse to a concise Complete state. The next action advances after returning from Android settings. Finish setup only appears when all stages are complete; Finish later explicitly shows the remaining count and records the user's choice without claiming grants. Back does not mark setup complete. Denied app permissions have a settings recovery action. Setup does not override revocations or automatically enable accessibility. Existing users see the new setup once on upgrade.

Messaging setup now uses the same numbered/progress presentation. Additional SIM, contacts, number, privacy and recovery controls are under Options & recovery. Entering from general Setup returns to that checklist through Back to setup instead of losing progress.

Android approval is explicit and persists until revoked or reset by Android. Sideloaded installations may require Android's restricted-settings approval before accessibility can be enabled. OEM menus and available power actions remain Android-owned. Physical Samsung enablement/reboot/service recovery and native menu display require handset acceptance; this environment must not shut down/restart a user's device as a test.

Reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#GLOBAL_ACTION_POWER_DIALOG

## Verification
Final production Kotlin/runtime compilation passed. Four focused API35 Robolectric/Compose checks passed: registered/permission-protected service, native global action dispatch and revocation/disconnect behavior; incomplete setup progress and explicit exit; landscape persistent actions/scrolling; and Power access settings. Portrait (360×720) and landscape (640×360) screenshots visually reviewed. This is host verification, not a physical Samsung power-menu/reboot test. No new APK built.
