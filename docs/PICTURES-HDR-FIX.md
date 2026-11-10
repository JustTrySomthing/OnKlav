# Pending picture recovery and stronger HDR

Source update after 0.100.15-rc1/code124. It includes the previous visualizer/gesture update, which also has not yet been packaged in a new APK. No APK was requested in this source-edit turn.

## What the screenshot establishes

The disabled retry buttons and carrier-verification message show the app's local carrier-trial preference is off. They do not establish a specific carrier/APN failure. The screenshot's older "Carrier download failed (0)" wording predates the current error mapping. Android documents RESULT_OK as successful MMS download, with specific MMS error constants for failures; zero is not the documented MMS success result. See https://developer.android.com/reference/android/telephony/SmsManager#downloadMultimediaMessage(android.content.Context,java.lang.String,android.net.Uri,android.os.Bundle,android.app.PendingIntent).

## App-side MMS changes

Pending Pictures now provides explicit Enable picture downloads and, when needed, Choose default messaging app actions. Enabling uses the existing explicit handset-trial setting. MMS_READY remains false; no default role or trial is silently enabled, and no outgoing message is resent. Retained requests stay available. Status refreshes while the dialog is open, correctly distinguishes waiting/active/expired states, and normalizes the older code-zero error text. Different permission/gate failures have distinct instructions.

Incoming downloads use a unique output file and callback token per attempt, mapped durably to the original pending job. A stale previous callback cannot clear or fail a newer attempt, change its output, or select another SIM. Legacy callbacks are accepted only when the legacy job has not advanced to the new attempt scheme. Success still files once; best-effort acknowledgement cannot relabel a filed picture as failed.

A persisted three-minute watchdog converts stalled attempts to retained, bounded incoming retries. Cancelled/zero callbacks are retryable, along with existing transient connection errors. Retries remain limited to three automatic retries, preserve the receiving SIM and location, and stop for revoked role/disabled downloads. Startup recovery now also checks incoming download jobs. Android owns the underlying MMS transaction; this does not claim that OnKlav can cancel its system network request. Late callbacks are isolated by token instead.

## Image Editor

Removed Technicolor, Pencil drawing and Comic book from the effect wheel and rendered saved edits, while retaining the draft parameter parser/history format. Existing drafts with those keys now render without those effects. The previous removal of Fixed canvas/Fit image/Fill canvas/Reset geometry and Move horizontally/Move vertically is retained; pinch/drag remain the direct geometry controls.

HDR now uses stronger medium-scale local-detail enhancement and tonal shaping across the existing 0–100% slider. Endpoint preservation, opacity, dark-noise gating and hard-edge halo protection remain. A separate smoothed luminance layer prevents single-pixel grain from becoming added texture; the hard-edge guard still uses original luminance against the broad base. It is a local SDR enhancement, not recovery of clipped highlights or true multiple-exposure HDR.

## Validation and remaining acceptance

See pictures-hdr-verification.json and docs/evidence/pictures-hdr-fix for final test/lint evidence. Coverage includes actual Android request parameters, original SIM, unique callback/output identity, stale callback isolation, bounded watchdog retries, retained notifications/receipts, disabled/expired dialog actions, removed looks, HDR local structure/strength/tones/halos/alpha, photo gestures/crop consistency and shared transport durability. Portrait/scenic strength samples are reviewed from native rendered output.

Actual affected MMS payloads, modem/carrier traces and handset access were unavailable. Carrier downloads, APN/subscription setup, message expiry, OEM background scheduling and real-phone acceptance remain unverified. No claim that an actual carrier outage or all MMS delivery failures are solved.

Next requested APK must increment the host version, include all current source, preserve the original Android signer/native/SDK identities, refresh generated packaging caches and run the normal APK verifier. No core/native/SDK Wasm/game rebuild is needed while existing proofs match.

Validation corrections: two older transport durability fixtures lacked any corresponding pending-job metadata, so the new unknown-callback guard correctly ignored them. The fixtures now stage known incoming jobs before injecting failed commits; both original durability assertions pass, and a separate unknown-callback test verifies no ghost job/error is created. Initial HDR review caught excessive fine-grain boost; the new medium-scale layer fixed it. Its first edge guard produced a failing dark-edge halo check; restoring the guard to original luminance fixed that check. The final full HDR suite passes unchanged thresholds. Earlier logs are retained as diagnostics. The earlier lint invocation was interrupted when HDR source changed; only the final full-lint.log is authoritative.
