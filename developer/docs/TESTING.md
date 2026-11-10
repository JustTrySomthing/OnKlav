# Testing and troubleshooting

## Fast feedback

Run `python3 developer/onklav.py doctor` once after installing prerequisites.
Build your app, inspect the output, then run its scenario. The tools do not rebuild
OnKlav or its games. Each build command has a240-second limit rather than an
unbounded retry loop. If a command times out, inspect the failure before retrying.

A project scenario is JSON:

```json
{
  "grants": ["storage.private"],
  "steps": [
    {"action": "title", "value": "Draft"},
    {"action": "body", "value": "Keep this"},
    {"action": "save", "expect": {"status": "Saved"}},
    {"reopen": true, "expect": {"doc.text": "Keep this"}}
  ]
}
```

`expect` paths address the returned frame's `state`. Values are compared exactly.
`reopen` closes the guest session and launches a new one. A step can specify
`pending: "clipboard.write"` to assert that a host action was requested; this
checks the pending request, not Android's clipboard. A scenario requiring an OS
reply must use the raw harness or a real phone; the tool never fakes success.
Test roots are temporary and cannot change the installed Android app.

## Raw headless harness

```sh
python3 developer/onklav.py host --data-dir ../temporary-app-test-data
```

Send one JSON object per line. Each reply is `{ "ok": true, "value": ... }` or
`{ "ok": false, "error": "..." }`. A process retains live sessions; restarting
it loses session tokens but keeps explicitly saved test data. Do not use real
phone backup data or a valuable directory as a test root.

1. `{"action":"stage","path":"/absolute/path/to/app.onklav-a"}` returns review.
2. `{"action":"install","hash":"<review hash>","grants":[]}` installs with no grants.
3. `{"action":"permissions","id":"org.example.notebook","grants":["storage.private"]}` chooses test grants.
4. `{"action":"launch","id":"org.example.notebook"}` returns `session` and `frame`.
5. `{"action":"event","session":"<token>","event":{"action":"save","value":""}}` sends a visible action.
6. `{"action":"close","session":"<token>"}` closes it.
7. `{"action":"remove","id":"org.example.notebook","delete_data":false}` retains data.

Other production host-broker operations are intended for platform tests. Raw host
requests are not available to guest Wasm. OS-service requests return `pending`;
manual `reply` requests are test fixtures only and cannot certify actual device
success. Do not automate confirmations that a phone user should see.

## Typical failures

| Error/symptom | What to check |
| --- | --- |
| cargo/rustc missing | Install Rust, reopen the terminal and run doctor. |
| wasm target missing | Run `rustup target add wasm32-unknown-unknown`. |
| linker unavailable | Install native C/MSVC build prerequisites. |
| locked build requests changes | Commit/regenerate Cargo.lock intentionally; do not remove `--locked` silently. |
| package/output already exists | Increase app version and choose a new output filename. |
| unsupported/invalid manifest | Use only documented fields, capabilities, IDs and integer versions. |
| imports forbidden | Use the plain Wasm target and broker effects, not WASI/native/network imports. |
| missing memory/alloc/step export | Use `onklav_app::app!(step)` and one `cdylib` target. |
| access is off | Declare the capability, obtain the phone user's grant and handle denial. |
| session changed/revoked | Open a new session after grant changes, update or removal. |
| direct button tap required | Start the protected effect from a real user action, not startup or a callback. |
| signature/publisher mismatch | Use the original private key and unmodified package; do not rotate identity. |
| newer signed version required | Increase integer version; a same-version replacement is not an update. |
| private quota reached | Delete/compact your own documents; never delete another app's files. |
| WebView profile unavailable | The phone's WebView provider must support the required isolated profiles. |
| speech voice missing | Use the included shared Nano engine and Male/Female choices; retry host voice setup. App packages do not bundle engines. |
| field edit not yet reflected | Native typing is debounced500ms; navigation flushes edits. |

Do not log private document text, contacts, tokens or signing keys in release
reports. A signature proves publisher continuity, not that an author is trusted.

## Acceptance included with this release

`python3 developer/tests/test_workflow.py` creates disposable keys/projects and
runs nine independent-author lifecycle/security/tooling cases using real Wasm and
the production Rust broker. `python3 developer/tests/test_contract.py` checks
published contract/component lists and required SDK content against the source.
These are bounded SDK checks; they do not start an emulator or test games.

On a phone, check the installer review, permission denial/revocation, theme/font
sizes, small-screen layouts, keyboard/Back, app close/reopen, sharing/pickers and
any native feature your app uses. Desktop tests cannot establish those results.

## SDK0.4 checks

`check PACKAGE` verifies signature, installation and startup without grants.
`build` also performs that check and removes an invalid output. Default filenames
include the version (`org.example.notebook-v1.onklav-a`). `test` runs the project
scenario; providers/media/speech still require Android phone acceptance.

Runtime requests stop after30seconds without a reply, killing the stalled host.
Build commands are bounded to240seconds. A source fingerprint invalidates stale
local core/harness build outputs. Inspect errors before retrying; there is no
automatic retry loop. Reference app scenarios check startup only; they do not
simulate provider/media success. Document/settings scenarios test persistence;
files/viewer scenarios cover browsing and cancellation.

## One-command local verification

`python3 developer/onklav.py verify PROJECT` compiles, signs, checks installation/
startup and runs the project's scenario in an isolated root. The signer/package
are disposable; no permanent author key or device install is required. Use
`build --key YOUR_KEY` for a distributable release. `catalog QUERY` finds helpers.

Scenarios can assert `frame_expect` paths in addition to state `expect`. Dotted
paths may index arrays (`items.0.id`); missing paths report a clear assertion.
Examples have runnable scenarios. Generated form/list scenarios test persistence;
reference native/provider examples check startup only and require app-specific
and real-phone acceptance. Do not replace meaningful assertions with an empty
scenario to make a failing feature look tested.

Cargo/build process groups have finite deadlines on POSIX; the runtime response
reader also bounds blocked writes and missing replies. No automatic retry loop
runs. Source fingerprints and a linked host build/source handshake reject stale
desktop brokers. Initial dependency downloads may be needed; inspect a failed
command before retrying. Native failures are rerun only after a concrete fix.

`upgrade PROJECT` updates portable SDK dependencies with a retained backup and
rollback on dependency-update failure. It does not change your app ID/key/version,
source logic or installed phone app. Review changes, verify, then bump your app
version for a release.
