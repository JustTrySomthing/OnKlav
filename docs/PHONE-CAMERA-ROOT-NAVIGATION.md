# Phone and Camera bottom-menu refinement — 2 November 2026

Phone and Camera now open as root destinations in the same persistent bottom menu as Apps, Messages and Groups. Switching to either destination replaces the workspace immediately; it does not enter the right-slide tool route. Their root headers have no exit/back arrow. Standalone Phone dial/add-call activities and Camera’s internal capture-gallery navigation retain their meaningful back actions. Android Back continues to work.

## Dialer

Keypad, Recents and Contacts are compact top tabs, leaving one clear bottom action row. The 68dp call button is centered between Save and number options, with 16dp vertical padding separating it from the main menu. Number entry, keypad letters, voicemail, speed dial, Recents and Contacts remain available. Keypad density adapts to available content height and large text; short displays can scroll the keypad while keeping Call and both navigation rows accessible. Embedded screens use the home screen’s insets rather than adding a second set.

## OnKlav call screen

The project already implements the native Android default-dialer contract: an exported InCallService protected by BIND_INCALL_SERVICE, IN_CALL_SERVICE_UI=true, a private incoming/ongoing call activity, role-first setup, and outgoing calls through TelecomManager.placeCall. These registrations were preserved and checked, including explicit routing back to the private OnKlav call activity. System ringing remains enabled through IN_CALL_SERVICE_RINGING=false.

The Phone workspace now offers **Use OnKlav**, directly opening Android’s Default Phone role request. Selecting OnKlav is required for Android Telecom to deliver ordinary incoming/ongoing calls to its call UI. Tapping Call without the role opens this same consent; runtime access remains in the existing setup flow. No automatic role assignment or alternative phone-account registration was added. Android retains emergency-call and platform-consent behavior.

Reference: https://developer.android.com/reference/android/telecom/InCallService (default Phone requirements, manifest example, role consent and emergency behavior).

## Verification

All 24 focused Android host cases passed: 4 root-navigation/role UI cases, 14 Phone data/manifest/Telecom checks and 6 Phone UI cases. Production and all test sources compiled; runtime bundle bytes match compiled Kotlin. Rendered Phone, large-text Phone and loaded Camera screens were reviewed. See phone-camera-root-verification.json and evidence under docs/evidence/phone-root-navigation. The initial host run found an incorrect role-extra name in the new test fixture; production uses RoleManager.createRequestRoleIntent and did not have that error. The fixture was corrected against the actual Android implementation. The Camera test waits for its loaded title before capturing evidence. Screenshot data are synthetic, and Camera permission is denied in the host fixture; camera hardware is not exercised.

Host tests cannot prove real carrier call routing, lock-screen/full-screen notifications, Bluetooth audio or OEM behavior. Verify incoming/outgoing calls on a physical phone after selecting OnKlav as the Default Phone app. No new APK was requested or built; the last delivered APK remains 0.100.34-rc1/code143 and predates these source changes and the previous six audit repairs. Original package/signing identities and source-matched native/SDK/game outputs are unchanged.
