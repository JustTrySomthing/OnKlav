# Phone incoming presentation and terminal-call cleanup — 2 November 2026

Owner report: overlapping Android-rendered answer notification and OnKlav call screen, an ongoing notification remaining after hang-up, and a persistent red badge.

## Changes

- Remove the Phone workspace's independent automatic call-screen launch. Incoming presentation uses the existing CallStyle/full-screen notification route; outgoing calls still open the private OnKlav activity.
- Track the resumed private call activity with an owner token. Suppress the ringing notification while its answer screen is visible and restore it if minimized. A late pause/destroy from an older activity cannot clear a newer activity's ownership. Restoring a minimized ringing notification does not request another full-screen launch, avoiding re-opening the screen the user just minimized.
- Incoming and ongoing calls use separate channels. Ongoing calls are low importance; neither live-call channel contributes an app-icon badge. The incoming channel has a new identity because previously created channels retain settings. Missed calls retain their separate, dismissible notification and history.
- Consume the state supplied to Call.Callback.onStateChanged. Disconnected calls remain privately tracked until Telecom removes/destroys them, but are excluded immediately from published live state, return-to-call UI, post-dial waits and the ongoing notification. Terminal state is sticky against late updates.
- Remove a Call on either removal or destruction, without double-processing missed calls. Clear session state and the ongoing notification on unbind as well as destruction. Guard publication against an older service instance.
- MainActivity resume cancels an orphaned ongoing notification only when there is no current in-process call service or published call. It does not clear missed-call notifications.

## Verification

Production and all test sources compiled successfully. All 41 selected/shared host checks pass: 10 PhoneCallLifecycleTest cases, 14 existing PhoneTest cases and 17 shared checks. Runtime bundle bytes match the compiled Kotlin classes. Results and source hashes are recorded in phone-call-lifecycle-verification.json. The initial run caught the pre-ringing NEW-state launch in an earlier compile snapshot; the final rebuild passes it, and both logs are retained. These are framework-host checks, not physical Samsung/carrier acceptance. No APK requested or built for this change; 0.100.38 predates both this correction and the saved landscape Camera correction.

The screenshot's card bears the OnKlav icon: its Android styling alone does not establish that Samsung Phone posted a separate notification. The removed automatic activity launch and foreground notification suppression address the duplicated OnKlav presentation found in source. Actual OEM/role fallback behavior still needs a device call.
