Preview feedback fix delivered in Diagnostics checks2 / Android versionCode79.

The signed sample renders instructions and safe result labels for store, read,
missing value and service failure. No secret or raw reply error is displayed or
reported. Null native-service feedback is omitted. The Android package and
signing certificate match checks1; this is an in-place diagnostic update.

A new sample ID avoids the old ephemeral publisher identity. Restarting checks
reuses an identical signed sample instead of attempting a forbidden reinstall.
A focused integrated Android host test verifies upgrade, restart, storage result
branches, report redaction, notification submission and null-service feedback.
APK signature, ARM64 JNI identity, fixture bytes and 16KB zip alignment verified.
Actual checks2 hardware results remain pending; checks1 camera, notification and
automated secure storage success were reported by the user.
