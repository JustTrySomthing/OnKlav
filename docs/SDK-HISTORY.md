# Historical SDK development notes

This is archived history, not current developer instructions. Use developer/README.md and developer/docs/API.md. Old limitations and build directions below describe their original checkpoint dates.

# OnKlav App SDK 0.1 — development preview

Only OnKlav is an Android APK. An app is a signed `.onklav-a` file installed,
reviewed, opened, removed and shared inside OnKlav. Files, Notes, RSS, Read Aloud, Browser, YouTube and Annotation are real
first-party SDK apps. Counter is a separate developer example; it is not added to
the default desktop. All run through the same installer, interpreter and renderer.
This SDK is incomplete: it currently supports small document-style applications.

App logic is Rust compiled to WebAssembly. Kotlin/Jetpack Compose in OnKlav draws
the declarative UI and brokers Android services. Package authors do not ship
Android activities, Java, DEX, APKs or arbitrary Kotlin classes. The Android host
still uses its normal JDK/framework build tools. Godot/GDScript remains the
separate planned game authoring path; external game execution remains disabled.

For existing bundled apps, start with `python3 scripts/build_sdk_apps.py --plan`;
see [selective build workflow](../docs/SDK-BUILD-WORKFLOW.md). Unchanged packages
reuse without Rust or publisher keys.

## Build and install a new author’s app

Install Rust 1.90 or newer and its `wasm32-unknown-unknown` target. From the
source root (all these files are included in the SDK starter):

```bash
rustup target add wasm32-unknown-unknown
mkdir -p "$HOME/.config/onklav"
cargo run --locked --manifest-path core/Cargo.toml --bin onklav-pack -- keygen "$HOME/.config/onklav/my-app-publisher.key"
RUSTFLAGS='-C link-arg=-zstack-size=524288 -C link-arg=--max-memory=8388608' cargo build --locked --release --target wasm32-unknown-unknown --manifest-path sdk/examples/counter/Cargo.toml
cargo run --locked --manifest-path core/Cargo.toml --bin onklav-pack -- pack sdk/examples/counter/manifest.json sdk/examples/counter/target/wasm32-unknown-unknown/release/onklav_sdk_counter.wasm "$HOME/.config/onklav/my-app-publisher.key" Counter.onklav-a
```

For a new author only, generate the key once at an absolute path outside the
source tree (choose a different private location if this checkout is under
`~/.config/onklav`). Never regenerate a key for updates or use a new key for
existing first-party packages. Keep that publisher key private and backed up. Share only the resulting package.
Choose a unique reverse-domain ID for your app. Increase the integer version for
updates; use the same signing key. OnKlav pins publisher identity even after
removal. A signature proves continuity, not that an author is trustworthy.

In OnKlav open Settings > Apps > Install App, select the package, review its
identity, publisher fingerprint and requested capabilities, then install. Open
it from Apps. Settings > Apps manages permissions, exports its installed package
and removes it with optional private-data deletion. Removing without deletion
retains private data for a later reinstall. External packages begin with no
granted capabilities; a user chooses grants. The curated bundled Notes package
receives its declared capabilities on initial bootstrap.

## Program contract

Use `sdk/rust` (`onklav-app`) as a path dependency. The Counter example is the
smallest complete program. `app!(step)` wraps a `fn step(Value) -> Value`.
Input contains `state` and `event`; return a frame with `title`, `state`,
`actions`, `body`, optional `drawer`, `menu`, `confirm` and `effect`.
Supported nodes are `label`, `button`, `field`, `editor`, and `record`.
Button IDs and field IDs become event action IDs. Field edits arrive after a
500ms host debounce; host navigation flushes edits first. State is transient;
explicit record services provide durable storage. Notes demonstrates search,
editing, confirmation, drawer/menu navigation and effect replies.

Effects contain `service`, `args` and `then` (inspect the helper/example for the
exact shape). The reply event action is `then`, with `reply.ok` and
`reply.value` or `reply.error`. Services available now:

| Capability | Services | Behavior |
| --- | --- | --- |
| `storage.private` | `records.current/new/list/load/save/delete` | App-private text documents; list returns at most 40 search matches |
| `clipboard.read` | `clipboard.read` | Plain text, explicit user action |
| `clipboard.write` | `clipboard.write` | Plain text, explicit user action |
| `files.export` | `files.export` | User-selected Files destination through OnKlav |
| `share.text` | `share.text` | Android share chooser through OnKlav |

Each service is checked by the Rust broker. Native text-selection copy/paste
also checks the live session grant. Apps receive no Android Context, filesystem
handle, network socket, WASI import, JNI import or raw clipboard. Permission
changes, updates and removal invalidate active sessions and pending requests.
OnKlav retains control of the renderer, navigation and Android permission flow.

## Package and execution limits

Manifest fields are schema=`onklav.app.v1`, runtime=`rust-wasm-ui.v1`, reverse-domain
`id`, `name`, integer `version`, publisher public key, and capabilities. The packer
inserts the public key and signs the exact manifest bytes plus Wasm digest using
Ed25519 with a domain separator. The ZIP contains exactly `manifest.json`,
`main.wasm` and `signature.ed25519`; the host rejects extras and unsafe paths.

Current limits: package 700 KB, Wasm 512 KB, manifest 16 KB, guest linear memory
8 MiB, 120 million interpreter fuel units per invocation, six service continuations
per event, one million bytes per input/output, 450 KB transient state, 100 UI
nodes and 150,000 UTF-16 units across UI text. Private storage is limited to 16 MB
and 2,000 documents per app, title 80/body 100,000 UTF-16 units. Memory, fuel,
import, signature, identity, capability and private-data isolation cases have
host tests. This is a new runtime integration, not an independently audited
security product. Old package blobs currently have no automatic garbage collector.

Network/feed, speech and web components now have initial SDK paths. The host still needs complete media, communications, rich drawing,
custom-icon and broader desktop-style UI services. Social is an intentional permission-free Hello World SDK placeholder. Walkie-Talkie
is an incomplete signed SDK app with private clip storage and foreground voice components; live communications remain unfinished. See docs/APPS-PORT.md for the full migration ledger.

## Feed services added in 0.11.0

`network.feeds` permits `feeds.fetch`, with `storage.private` also required. The
host checks/fetches/parses a public HTTPS feed and the Rust broker commits its
verified result into app-scoped storage before replying with metadata. Other
`feeds.*` operations (state, query, catalog, article, bookmark, follow, edit,
remove, settings, clear_cache, reset_read and reset) require private storage.
See core/src/feeds.rs and sdk/apps/rss for exact development-preview schemas.
The original limits are 32 feeds, 40 items/source, 600 cached items, 200 saved
bookmarks and 4,000 read marks; the feed store is capped at 8 MB within the shared
16 MB per-app quota. Query pages have 20 items. `network.web` permits a button
initiated `web.open` in the host's text-oriented publisher viewer. JavaScript,
local files, downloads and web device permissions are disabled in that viewer.

New generic widget kinds: toggle, select and horizontally scrolling chips.
Select/chips provide options with string value/text fields. Records may include
a trailing action (id/text/icon/selected). These use the same validated action
and capability boundary as the existing widgets.

## Speech components (0.12 development)

Apps may declare `speech.output`. A `speech` widget requires an id and text `value` of at most 50,000 UTF-16 units; it supplies host-owned playback, bounded chunks, seeking and audio lifecycle. A `speech_settings` widget supplies installed offline voice/rate/preview controls. The host validates the current widget/session/grant on use and stops on revocation, background or close. This component does not expose Android Context, TTS objects, OS permissions or network synthesis to Wasm. See `apps/reader` and `docs/READER-PORT.md`; original bundled voices remain pending.

Adjacent button/field nodes can share a short `row` string for horizontal arrangement; no nested code or arbitrary views are accepted. Optional frame `counter` renders a compact header count. Field/editor `reject_overflow:true` retains current text for oversized edits. Event selection ranges include `selection` and `selection_end` in UTF-16 units. All existing node/text/fuel/memory limits still apply.

## Browser/web components (0.13 development)

`browser.*` services store tabs, bookmarks and settings under the current app’s private Rust scope, with `storage.private`. See `apps/browser` and `docs/BROWSER-PORT.md` for limits, all service operations and remaining gaps.

A visible `web` node has `id`, HTTPS `url`, validated 32-character `tab` ID, `on_change` action, numeric `seq`, command (`load`, `back`, `forward`, `reload`, `find`, `find.next`, `find.previous`, `find.close`, `clear`), optional `find` text and `javascript` flag. Only one web node is accepted, in the body. A provider supporting MULTI_PROFILE is required. Native profile identity comes from the authenticated host session, never the widget. No guest-provided JavaScript is evaluated and no native JS bridge exists.

Every web component requires `network.web` plus both clipboard grants, including JavaScript-disabled components, because native web controls have clipboard behavior. Host callbacks carry current tab URL/title/back/forward as non-gesture events; they cannot authorize clipboard, share or external-launch services. Native web menus/keys/accessibility also use live clipboard checks. Website storage/profile resources are separate from the Rust document quota. Current erase-on-uninstall and complete profile-cache cleanup remain unfinished; see the gap ledger before production use.

`icon` nodes are compact native actions with a validated ID, label, icon name, optional badge/enabled state, and optional adjacent row. A field may specify `submit` pointing to an existing action; the host still validates that action when invoked. `links.external` requires a separate declared/reviewed grant and a direct user button tap. `share.text` optionally supports `inline:true` for normal native text sharing instead of exporting a text file.

## Video viewer SDK additions (0.14.0 host)

The complete authoring example is `apps/youtube`, producing `packages/YouTube.onklav-a`. Its controller is Rust/Wasm; its library uses app-scoped `videos.state/visit/bookmark/query/remove/clear_history/reset` under `storage.private`. Query pages contain at most 20 items; history/bookmarks are bounded to 1000/200. `visit` accepts `record:false` to update bookmark titles while suppressing cleared current-video history. Video times are host-generated milliseconds, never guest-supplied.

A validated `web` node may request `provider:"youtube"`, `on_notice`, `epoch` and `command:"reset"`. Native navigation/account/external/error callbacks are non-gesture events and must match the live tab/session/epoch. Provider navigation is signed-out only. `media_record` validates an allowed URL, matching derived video ID and timestamp; `copy`, `delete` and `long_action` are unique action identities. A root `back_action` handles system Back; `header_loading`, `full_bleed`, `hide_header_landscape`, `close_requested` and bounded drawer dimensions select shared host presentation.

`network.thumbnails` permits only derived bounded provider thumbnails, not arbitrary image/network access. The native `host_image` descriptor binds the visible media record to the authenticated package; cached access needs live `storage.private`. `images.clear` is a direct-user-action host effect under storage.private. `links.external` optionally targets the installed YouTube Android application through target `youtube`; it remains an explicit granted operation. The default is the external browser. No external application is installed by this operation.

Provider scripts are fixed host code, never guest-supplied. This adds no raw JavaScript bridge. See ../docs/YOUTUBE-PORT.md for website/device/privacy limitations, including incomplete native-cache purging and internal Browser handoff.

## Image/canvas services (0.15.0 source checkpoint)

See `apps/annotation` and `../docs/ANNOTATION-PORT.md`. `images.edit` plus
`storage.private` permit scoped `drawing.current/list/load/rename/edit/text/undo/redo/delete`.
The image and full mark/undo data never enter Wasm state. One validated `canvas`
node in the body contains document ID/revision, tool, color, width, callback
and control-action identities. The native host fetches the current scoped
document through `host_canvas`; `canvas_event` checks session, ID and revision
and forwards completed image-space edits or text taps as non-gesture events.
These cannot launch OS effects. Viewport pan/zoom is transient and never exported.

`images.import` requires `files.images` as well as image/storage grants and a
direct user action. The native broker lets the user choose a content URI,
normalizes a bounded picture, then Rust commits only a validated image and
returns metadata to Wasm. Packages never receive the URI or base-image bytes.
`images.text` measures native text and Rust validates the resulting image-space
box before committing. `images.export` takes a document ID/revision and mode
`files`, `share` or `copy`, requiring respectively `files.export`, `share.text` or
`clipboard.write`, alongside image/storage grants. Only the native host receives
the scoped render snapshot; permission/session checks repeat after rendering
and when a selected Files destination returns. Clipboard images use a temporary
read-only provider URI. `share.text` now covers reviewed text or image sharing.

Library `image_record` nodes fetch only the authenticated visible scoped record.
`palette` and `slider` use validated choices/1–12 widths and normal action events.
`undo_action`, `redo_action`, `tools_action` are unique widget action aliases.
`menu_bottom:true` places shared dialog chrome at the bottom for drawing tools.
The current source adds these host APIs; earlier 0.14.0 APKs reject the new
capabilities. Wait for the next OnKlav host build to install Annotation.

## Social and Walkie packages

`apps/social` is the requested permission-free Hello World placeholder.
`apps/walkie` v6 is an incomplete signed SDK app with scoped preferences,
prepared recipients, a bounded clip archive and foreground voice controls.
`walkie.clips`, `walkie.clip.delete` and `walkie.clips.clear` require
storage.private; trusted archive writes/outcomes are unavailable to guests.
Voice capture/playback require the explicit grants below. The dial stays
OFFLINE and Connect reports unavailable; live chat/SMS/MMS are not implemented.
Existing installs update through the normal signed package review flow.
See docs/WALKIE-PORT.md for exact tests, device limits and remaining work.

## Voice components (0.19 development)

`audio.record` and `audio.playback` are separate explicit, revocable grants.
Both default off for curated bootstrap, including Walkie v3. Android additionally
asks the user for OnKlav microphone permission after a Record press. Granting
that OS prompt never automatically starts recording.

A `voice_capture` body widget declares id, normalized peer and on_change; its
recipient must match the app's saved walkie.prepare/selected record. The host
provides Record/Stop, Preview, local Queue and Discard. A `voice_clip` declares
id, clip (private clip ID), text/detail and delete action. It provides Listen/Stop.
Private audio never enters guest state, events, filesystem paths or WASI imports.
Only host code can use host_voice/voice_prepare/voice_commit/voice_played.
Successful capture queue sends an on_change event with clip id/state=queued,
with no direct-gesture authorization for additional device effects.

Recording uses foreground permission checks, focus, generation tokens, private
cache files, 12-second and 64 KiB limits and Rust structural AMR validation.
Playback requires current foreground authorization and marks an incoming clip
heard only on successful completion. Outgoing playback never proves remote heard.
Any navigation/modal/recipient or permission/lifecycle interruption discards drafts.
These are local voice-message components. Live chat, sending, receipts and connected
Auto Play/Auto Send remain unavailable; see docs/WALKIE-PORT.md.

## Single-contact picker (0.20 development)

`contacts.pick` is an explicit revocable capability, default OFF for bundled
apps. A direct button event may request `effect("contacts.pick", {}, then)`.
The host opens Android's phone-row chooser (ACTION_PICK/Phone.CONTENT_URI),
reads just the chosen row and replies with `{phone, name}`. Rust normalizes the
phone and restricts names to 80 UTF-16 units without control characters. No
address-book enumeration, URI or READ_CONTACTS permission is given to Wasm.
Cancel/failure leaves the prepared recipient unchanged. Pending tokens and
current grants are checked again before reading and returning the selection;
revocation/update/removal invalidate the session and discard late results.
Selection is not compatibility verification or consent to send an invitation.
Walkie persists optional display names in the same private recipient records;
old records without names still load. Only OnKlav remains an Android package.

## Outgoing session foundation (0.21 development; transport disabled)

walkie.state now reads the bounded private session snapshot and recovers a
pre-restart session as ended/interrupted. walkie.connect reaches the Rust session
controller's closed transport gate: no session or invitation is created. Quit,
clear recipients, or changing selected recipient cancel a pending session before
changing selection. The signed controller displays recovery reasons and a compact
connection-unavailable dialog with Cancel/Try Again; the dial stays OFFLINE.

The trusted host action `walkie_session` is fixed to installed Walkie and its
storage grant. Guests cannot issue it as an effect or supply a transport-ready
flag. Only state/end are presently enabled. Begin/claim/sent/receive reject all
production calls. Unit tests exercise the private machine with fixture transport
availability; these are not live session or identity authentication acceptance.

The staged OnKlav control namespace is !OKW1. A random session nonce and exact
normalized source phone + SIM bind P probe/C reply, I invitation/A accept and
D decline; it does not treat legacy !ICW1 controls as OnKlav verification. This
namespace is not an implemented receiver or a claim of backward compatibility.
Correlation is not cryptographic peer identity/authentication. Protocol review,
incoming consent, rate/blocked policy, permission broker, lifecycle ownership and
actual SMS/MMS transport must be implemented before enabling communications.
Never enable the gate solely by flipping a constant. Full TODO 3b stays open.

## Walkie invitations (0.22 development; transport still disabled)

communications.walkie is explicitly granted/revocable and defaults OFF. It is
independent of contacts.pick and audio.record/audio.playback. Connect/Accept
require it plus a direct gesture; the compile-time closed transport gate is
checked after permissions. Host session state/end remain storage-only; every
other trusted session action requires communications.walkie. Permission changes
invalidate SDK sessions and cancel pending control/verification state. Guest
walkie.decline dismisses the exact pending invitation locally without a carrier
response. Raw incoming controls and send outcomes are host-only, not guest effects.

The staged incoming controller accepts !OKW1 P/I only after exact sender/SIM/session
correlation, host policy approval and accepted verification-reply outcome. Reply
claims are one-shot; limits are 30/day and process-monotonic 2s per sender/SIM, with 64 retained probe
nonces. A user Accept queues an A response; only a matched host accepted outcome
can reach control-connected. Pending verification and invitation expire after
45s; restart interrupts both and retains the daily quota. This is not a real
receiver or cryptographic peer identity, and the fixed gate is not user-configurable.
See RESUME-0.22.0.md for limits and the remaining transport/confirmation/lifecycle work.

## 0.23.0 — Walkie carrier/audio integration; not operationally accepted

Signed Walkie v7 now calls the host-owned carrier bridge for invitations,
consent, floor-controlled audio, offline Send, explicit Retry and receipts.
Rust validates peer/SIM/session/attempt and live grants at the final handoff;
Kotlin owns SmsManager, protected receiver/results, private audio and policy.
Warm-up, beeps, foreground Auto Play and Auto Send are integrated. Successful
carrier/audio tests use fixtures. MMS_READY remains false; default role unchanged.
No phones are available, no real messages/audio were verified and no APK built.
Background/restart/late-arrival behavior, cleanup review and full original UI
acceptance remain open. TODO 3/5 stay incomplete. See RESUME-0.23.0.md and
source-verification.json for production paths, limitations and final evidence.

0.24.0 Settings integration: trusted host walkie_preferences reads/writes the
installed Walkie private store under storage.private, without adding permissions.
This is not a guest service or a new capability. Settings metadata backups do not
include SDK grants, installed executable code, pending sends or app private data.
See docs/SETTINGS-PORT.md for current UI and service limitations.

## 0.25.0 — automatic message YouTube previews

Privacy's default-off Automatic YouTube Previews switch is connected to shared
Messages/Groups video cards. Explicit Play stays available when thumbnails are
off. Bounded validated-ID CDN requests, cancellation/cache invalidation and an
internal signed YouTube SDK v2 handoff replace the disabled control. Existing
YouTube v1 installs use a reviewed Update Included App action. No silent grants,
APK, real provider/playback/account/carrier acceptance. See RESUME-0.25.0.md and
source-verification.json; other Settings services and handset checks stay open.

0.26.0 adds a host Settings SMS/MMS history exporter (Kotlin provider adapter and
Rust streamed ZIP/schema/checksum verification). This is not an SDK capability:
packages cannot enumerate messages or call the backup operation. Internal app
packages and their grants are unchanged. History restore remains unfinished;
see docs/HISTORY-BACKUP.md. No APK/mobile ABI build for this checkpoint.

0.27.0 adds strict host history ZIP inspection/review; it is not a package
capability. No SDK app can read message history or call this native operation.
The imported archive never enables writes, grants or executable code. Internal
packages/publishers are unchanged. See docs/HISTORY-BACKUP.md; full provider
restore and handset acceptance remain unavailable, with no per-item APK.

0.28.0 adds host-only read-only history content comparison and mapping candidates.
READ_SMS belongs to the explicit host Settings action, not an SDK app permission.
Guest packages cannot call it; no provider writer/grant/package change is added.
See docs/HISTORY-BACKUP.md. Actual restore and phone acceptance remain unavailable.

0.29.0 adds a gated Kotlin restore executor/Android adapter and durable Rust
per-message journal; Settings reads/recover-classifies pending outcomes. This is
backend progress, not enabled restore. Known incomplete writes can be cleaned;
unknown insert results block retries, committed IDs remain recorded. Archive
apply orchestration, final metadata merge, journal compaction and real phone/MMS
acceptance remain open. See RESUME-0.29.0.md and HISTORY-BACKUP.md. No APK.

0.30.0 adds the gate-closed archive-to-executor coordinator and durable partial/
complete message outcome maps derived from committed journal IDs. Fresh archive/
plan binding, all-candidate preflight and host local MMS identities are required.
The Restore UI and phone writes remain disabled. Post-write provider revalidation,
folder/flag merge, recovery/configuration UI, compaction and phone/MMS acceptance
remain open. See RESUME-0.30.0.md and HISTORY-BACKUP.md. No APK.

0.31.0 adds post-insert provider capture/content matching and conservative folder/
flag merge with current-entry precedence and atomic state/completion receipts.
Imported reminders and stale thread unread overrides are disabled. Fixture tests
are not actual provider/phone acceptance; Restore UI and MMS gate remain disabled.
Recovery/configuration UI, durable handoff/compaction and phone acceptance remain.
See RESUME-0.31.0.md and HISTORY-BACKUP.md. No APK.

0.32.0 adds ReferenceDialog restore settings/recovery review, staged private local
MMS identities, interrupted-insert review and saved outcome count/detail pages.
Receipt-backed Finish saved record only repairs acknowledgement; unknown inserts
stay paused. Cleanup and Restore messages remain gate-closed. Compaction, actual
resume/apply review and real phone acceptance remain unfinished. No APK.
See RESUME-0.32.0.md and HISTORY-BACKUP.md.

## Shared presentation, 0.42

A `record` may set `card: true` for a rounded source-first reading card. `text`
is the headline, the first `detail` line is source metadata and remaining detail
is a three-line summary. Existing plain record layout remains unchanged. The
normal trailing action owns its own 48dp target. Shared icon names select the
original borderless artwork; SDK applications use the same host primitives.
RSS v2 is the first-party example. No Android package per app is required.

## Files services (host 0.47+)

Files is the ninth built-in SDK app; Counter is the tenth program and remains a developer example. See docs/FILES-APP.md in the source package (FILES-APP.md alongside the SDK documentation). The shared library exposes `library.list`/`library.info`, `library.folder`/`library.rename`/`library.move`/`library.copy`, Trash/restore/purge and host-brokered import/open/export/external services. `files.manage` controls shared OnKlav content; `files.external` controls user-connected Android folders; copying to Android also needs `files.export`. Use opaque entry IDs; host paths and URIs are not guest API. Writes and picker operations require a user gesture; capabilities and tokens are checked at completion. Text exports default to Documents/<app>, image exports to Images/<app>, public browser downloads to Downloads/<app>.

The package builder validates source manifest fields against the signed manifest while allowing the packer to inject the existing publisher identity. It preserves publisher and update-version continuity; do not create replacement signing keys.

Host 0.51 UI hints: body button nodes may set `primary: true` for a prominent themed button; the `contacts` icon is available to all SDK apps. These hints grant no capabilities. Walkie v8 uses them for its selected-recipient presentation. See docs/RESUME-0.51.0.md.

## Optional scroll identities (host 0.57+)

`scroll_key` and `drawer_scroll_key` may be strings of at most 512 UTF-16 units,
without control characters. Change keys when the body/drawer represents a different
article, filter, query or page; reuse the list key when returning from detail.
The host preserves at most 32 positions per app model. Do not put secrets in keys.
These are presentation hints, not durable guest navigation or process recovery.


## SDK0.4 / host0.70

Current developer instructions are in developer/README.md. Included apps now
link the same public controller crate. Forms/lists/routes, portable projects,
catalog/verify, cookbook projects, signed icons/minimum host and safe old-blob
collection are implemented. See docs/SDK-REVIEW-0.4.md for evidence and limits.
