# Qualification checkpoint — not a release gate pass

39 and40 remain unavailable in this environment: neither adb nor the saved SDK
platform-tools executable exists, and no USB bus directory is exposed. No phone,
TalkBack/media/IME/process, carrier or two-phone test was run. Historical handset
results do not qualify this source. No APK/emulator was made or booted.

41 now has a limited host-side checkpoint. `scripts/qualify_host_sdk.py` builds an
optimized disposable Rust runner using the actual public SDK. It warms100 times,
then measures600 iterations each of20-header JMAP parse/merge,2048-point GeoJSON
route parsing, and weather parsing/imperial native-node construction. Each run
has a20-second internal deadline,30-second process deadline and120-second compile
deadline. Source hashes are checked unchanged across the final run.

The report records p50/p95/max microseconds and Linux process VmRSS after each
batch. These are short synthetic Linux measurements, not allocator heap counts,
Android RSS/PSS, sustained phone profiling or UI/frame latency. Inputs are reused;
there is no network/provider, Wasm guest execution, JNI, Compose or carrier load.
No pass threshold or leak-free conclusion is invented. The initial pre-fix report
is retained separately; the final report describes that historical source snapshot. The later workflow
wrap changes SDK/core/Kotlin and needs fresh sustained qualification. Repeated runs/cases are not added as unique coverage.

## Focused source review

| Area | Inspected production path / evidence | Outcome / open gate |
| --- | --- | --- |
| Signed HTTP | sdk_http validation and SdkHttpService exact origins/public DNS, live grants, timeout/no redirect/no retry, one-shot POST | Seven selected Rust policy/broker checks passed; actual Android transport/dependency/provider gate remains |
| Private files | sdk_storage bounded canonical storage/index/staging and live native-transfer broker ownership |16 selected storage/broker checks passed; Android provider/OS behavior not inferred |
| PDFs/maps | Visible signed owner/private blob binding, isolated PDF worker and map decode checks, lifecycle cancellation in existing source | Source inspected; existing scoped evidence preserved, no fresh hardware acceptance claimed |
| Mail confirmation | Exact reviewed draft, optimistic durable sending lock, one-shot HTTP and uncertain restart lock | Preserved; malformed receipt acknowledgement needed hardening |
| Mail acknowledgement fix | email_compose::accepted validates two unique correlated Email/set + EmailSubmission/set results, matching account, nonempty bounded IDs, no contradictory notCreated | New targeted malformed/duplicate/wrong-account/conflict case plus nine email regressions passed; failure stays uncertain/locked, never accepted or delivered |
| Dependencies/adversarial/OEM | cargo-audit unavailable; no authenticated provider/device/fuzz/penetration campaign executed | Remains open; this source review is not a comprehensive security certification |

Core/Kotlin source did not change. The exact working Linux/ARM64 native hashes
still verify and are reused. Mail acknowledgement is a public SDK helper change;
no signed bundled app/publisher identity was replaced. No actual email was sent.
The later bounded MIME/upload/map/mail source workflows are implemented; see
docs/workflow-wrap-verification.json. Those changes do not inherit performance
or provider/device acceptance from this historical sample.42 remains deferred.

Evidence: docs/qualification-environment.json,
docs/qualification41-host-performance.json and docs/qualification41-review.json.
