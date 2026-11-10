# Phone and desktop refinement — 2026-11-01

- Phone and Camera remain registered native destinations, available permanently in the bottom navigation, but are excluded from the Apps desktop and folder contents. Existing memberships are retained without destroying layout data.
- Dragging an app into any part of a folder cell reserves that folder as the target. Previously only a small center region did this; an edge-first drag reordered the folder away before its 450 ms opening dwell. Same-window popup, drop/cancel behavior, folder paging and drag-out remain intact.
- Phone's ringtone/carrier/blocked-number external settings shortcuts are removed. Internal settings now show setup status, keypad sound/vibration, direct ring-volume control with Android-policy error feedback, voicemail access numbers per calling account, calling-account names, and confirmed unblock management. Blank voicemail override uses the SIM-provided number; this configures the dialed access number, not carrier voicemail provisioning.
- The existing native protected InCallService and private in-call Activity are retained. Phone's foreground workspace also opens OnKlav call controls when Telecom supplies a new call, rather than relying only on a service activity launch. Call status has a compact theme-colored badge and an explicit OnKlav call header. Answer/decline, hangup, mute, keypad, routes, hold, add, merge, participants and Telecom video controls remain native. Contact creation/edit/deletion and package-scoped messaging stay internal.
- Android role/permission consent and emergency calls are necessarily system controlled. Carrier forwarding/call-waiting provisioning is not falsely exposed as a working internal feature. No new runtime/connection engine or new APK is introduced.

Validation is recorded in phone-desktop-polish-verification.json. Real carrier incoming/outgoing calls, lock-screen/full-screen notification behavior, audio devices and OEM background restrictions still require physical-device acceptance. Host UI fixtures do not establish carrier reliability.

Official reference: https://developer.android.com/reference/android/telecom/InCallService
