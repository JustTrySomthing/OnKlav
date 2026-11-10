# Resume and reminder chunk — 0.9.1 development

The saved 0.9.0 source archive passed its ZIP integrity check. The restored APK
matched b256996da93c03c61eeb722de915691d88bdbb47fbffb68c1e05e8f87a86bbbd.
The temporary environment had lost its build tools and caches. Java initially
could not reach Gradle's download URL without explicit proxy configuration;
the installed system Java was a runtime rather than a complete JDK. The saved
records also identify software-emulator startup and page-turn test waits as
previous slow points, followed by successful bounded 0.9.0 tests.

Restored the existing OnKlav signing backup, a full JDK 17, Gradle 8.11.1,
Android platform 36/build tools 35.0.0 and Rust 1.90.0 for host tests. The mobile
Rust libraries and Godot pack are retained unchanged from the verified 0.9.0
source. This is a Kotlin-only messenger increment, not a claim of a complete
native toolchain rebuild. scripts/build.py now rejects a runtime-only Java
installation, forwards proxy settings, bounds dependency network calls and
includes the app unit tests in full builds. Signing material stays outside source.

## Original operation ported

Read Inner Circle 2.2.85 ReplyReminderUi and AttentionTimes as a behavior
reference. Authored new Kotlin/Compose chooser and calendar calculations;
no original Java implementation is included. The menu offers In an Hour,
Tonight, Tomorrow and Choose Date & Time, plus Remove Reminder when scheduled.
Tonight uses the reference's evening/late-night behavior; tomorrow uses local
09:00, including daylight-saving changes. Past custom times are rejected.
The reminder manager adds Open Message (conversation), Reschedule and Remove.
Exact scrolling to the referenced message and complete visual parity remain open.

The production path is Compose -> ReplyReminders/ChatStore -> NativeCore JNI ->
existing Rust messaging flags/atomic store -> Android AlarmManager. A stale
queued broadcast now reschedules a future reminder instead of notifying early
and consuming it. Cancellation works when notifications are disabled and clears
the matching notification; scheduling rejects a disabled app/channel.

## Verification

Current exact APK and test outcomes are recorded in verification.json and
evidence/v0.9.1 after the checks finish. Do not treat the older screenshots or
0.9.0 device passes as tests of this new APK. Full Messenger, carrier/default
SMS/MMS acceptance, Groups/social/walkie, SDK and original game ports remain
unfinished. MMS_READY remains false; no role change or external SMS is part of
this chunk. No physical-phone performance or notification/Doze certification.

## Runtime-harness recovery record

The full API 29 Google APIs software image did not reach boot completion within
300 seconds before installing any APK. Its stale hardware-qemu PID lock then
blocked the diagnostic restart; the lock belonged to this completed check and
was removed. The diagnostic run used four guest cores, disabled Vulkan and
added process-group cleanup. ADB connected and Android services progressed,
but boot completion again exceeded 300 seconds. No app test passed in those runs.

A separately verified AOSP ATD API 33 image is the last bounded environment
check. It has no KVM acceleration here. Pixel capture is optional/disabled for
ATD; all functional assertions remain compiled in ReminderDeviceTest, including
native date/time picker changes, Rust persistence, the queued-alarm guard,
manager rescheduling and removal. Current actual runtime outcome belongs to
verification.json, not the existence of this compiled test. The ATD image SHA256
is d17967001f453c4b82d09dd3d6931b938c16c9f2bf0e054b521293ca24e3b95e,
from the AOSP tools/base system-image registry:
https://android.googlesource.com/platform/tools/base/+/7bf1856df50fac20b1ddb25868c2e712ee54f3a8/bazel/toplevel.WORKSPACE

**Current runtime limit:** The API 33 ATD check also exceeded 300 seconds
during Android startup (after reaching odsign/runtime preparation). No APK was
installed and ReminderDeviceTest did not execute. No current-APK installation,
UI, notification or physical-device pass is claimed. APK structure/signature
verification and all 31 host/JNI/unit tests pass. Stop the emulator attempts here;
resume installation/runtime acceptance on a working Android device/environment.
