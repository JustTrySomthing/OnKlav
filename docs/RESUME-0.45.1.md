# OnKlav 0.45.1 — source and tooling cleanup

The Android application and last verified APK remain 0.45.0-dev/code52.
Production Android source/resources/configuration exactly match the stored
optimized APK input snapshot. No app runtime, native binary, SDK guest program,
Godot pack, signing identity, transport gate or user-data format was changed.

## Fixed

- A partial SDK proof could previously mark the host current despite missing
  package records. All nine expected packages and exact source inputs are now
  required, with shared per-package checks across APK and host-test entry points.
- Host checks previously inspected only recorded SDK source paths, missing new
  source files. They now use the complete shared freshness validator.
- Duplicate signed-package archive entries are rejected for cached reuse;
  malformed JSON proof shapes/records fail closed instead of crashing planning.
- Current README and agent guidance no longer conflict about app versions,
  APK delivery, native rebuilds, artifact retention or drawer direction.
  Superseded root notes are labeled historical under docs/history; past test
  evidence remains available. Historical chunk/status guidance is clearly labeled.
- SDK author instructions keep new publisher keys outside the source tree and
  explain one-time key creation and existing publisher continuity.

## Checks and limits

20 Python regression checks passed. Real host and SDK plans reuse the unchanged
artifacts. The extracted starter passes all14 selective-SDK checks and actual
no-op reuse for nine packages without Android host files, Cargo or a publisher key.
The Android production input snapshot is unchanged. A source scan found zero
Java application source files. Required upstream JVM/Godot dependencies and
active private-data adoption/recovery paths remain in place.

The 139 app checks and12 shared checks are retained 0.45 evidence, not fresh runs
for this tooling checkpoint. No new APK was assembled or relabeled. This pass
found and corrected build/documentation bugs; it does not claim additional
runtime/phone-speed improvements. Physical handset, carrier and complete rewrite
acceptance remain open; use TODO.md and REWRITE-STATUS.md for remaining work.
