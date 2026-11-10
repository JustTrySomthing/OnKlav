# Short SMS network error reporting — 2 November 2026

The owner supplied a failed short text showing SMS 17, modem 2, plus the warning that other parts may have been sent.

Android defines result 17 as RESULT_NETWORK_ERROR. The separate errorCode value is technology-specific diagnostic information; value 2 alone does not establish a carrier, SIM, IMS, coverage or application root cause. Reference: https://developer.android.com/reference/android/telephony/SmsManager#RESULT_NETWORK_ERROR and sendTextMessage callback documentation.

## Findings and changes

- The existing sender already asks SmsManager.divideMessage for the real part count, uses sendTextMessage for one part, and uses multipart sending only for multiple parts. No short-message workaround, automatic retry, silent SIM change or speculative carrier configuration is added.
- The error formatter previously appended the multipart warning to every SMS failure. It now reads the staged outgoing part count. Only an actual multipart send gets the other-parts warning; single or unknown part count gets a recipient-check reminder before resending.
- Network and modem errors have distinct readable reasons while preserving the original numeric Android and radio diagnostic codes.
- Both provider-backed and local outgoing message displays recognize the exact older OnKlav error format and regenerate the explanation using stored part metadata. This corrects an existing single-part card without altering its retained receipt, send status or original message. MMS/custom errors are preserved.
- The durable settlement window, callback identity validation, monotonic late-success reconciliation and explicit confirmed retry policy are retained. A carrier failure is not reclassified as Sent without positive evidence.

## Verification and remaining diagnosis

All 36 selected/shared host checks passed: 7 error-format tests, 4 durable receipt/settlement tests, 8 sending-SIM selection tests and 17 shared tests. Production and all test sources compiled successfully; runtime bundle bytes match the compiled Kotlin. Final results and hashes are in sms-network-error-verification.json. These checks cannot establish why the physical phone received a network failure for this send. Reproducing that fault with device telephony logs remains open. No new APK requested or built; the verified call and landscape Camera corrections are retained.
