# App API reference — host0.74 / build82 / ABI v1

## Author manifest

An unsigned `manifest.json` requires these base fields:

```json
{"schema":"onklav.app.v1","runtime":"rust-wasm-ui.v1","id":"org.example.notebook","name":"My Notebook","version":1,"capabilities":["storage.private"]}
```

IDs are at most128 bytes, contain at least two dot-separated segments, and use
lowercase ASCII letters/digits; each segment starts with a letter. Names have
1–60 UTF-16 units with no control characters. Version is an integer1–2147483647.
The packer inserts a64-character lowercase hexadecimal Ed25519 `publisher` public
key. Never include a private key or Android permission. Optional `min_host` is a
positive host build integer; this host accepts at most82. Optional `icon` has
exactly one `symbol` from contract.json or lowercase `png_hex`. PNGs are bounded
to6144bytes and128×128,8-bit RGB/RGBA, noninterlaced, without animation/text/
compressed profiles. The CLI validates chunks/CRCs and bounded pixel output;
Android checks the safe chunk subset and dimensions before decoding. Keep the
ZIP's existing three entries; no extra image file is added. Old packages without
optional metadata remain supported. Unknown manifest fields still fail closed.

The signed ZIP contains exactly `manifest.json`, `main.wasm` and
`signature.ed25519`. Duplicate entries, links, unsafe paths, unknown manifest
fields and extra files are rejected. `onklav-pack` signs the schema-specific
domain separator, exact manifest bytes and Wasm SHA256 digest. Use the supplied
packer rather than reproducing the signature algorithm.

## Program and ABI

Depend on `sdk/rust` as `onklav-app`, expose one `cdylib`, and use:

```rust
use onklav_app::{frame, node, serde_json::{json, Value}};
fn step(input: Value) -> Value {
    let mut count = input["state"]["count"].as_i64().unwrap_or(0);
    if input["event"]["action"] == "add" { count = count.saturating_add(1); }
    frame("Counter", json!({"count": count}), vec![], vec![
        node("label", "count", &count.to_string()),
        node("button", "add", "Add one")
    ])
}
onklav_app::app!(step);
```

Input is `{ "state": <previous JSON state>, "event": {...} }`. The first event
has action `start`. User actions contain `action` and usually a string `value`.
Fields/editors include UTF-16 `selection` and `selection_end`. Typing is debounced
500ms; host navigation flushes edits. Toggles send `"true"`/`"false"`; select/chips
send the chosen option's string value; sliders send a decimal integer string.
Callbacks are non-gesture events, and cannot authorize protected OS effects.

The helper exports `memory`, `onklav_alloc(i32)->i32` and
`onklav_step(i32,i32)->i64`. The result packs output pointer in the upper32 bits
and JSON byte length in the lower32 bits. A bounded Wasm instance is created per
invocation and discarded afterward. Do not retain native pointers, depend on
Wasm globals between events, or add WASI/JNI/native imports. JSON `state` travels
between invocations; it does not survive a closed session unless explicitly saved.

## Frame and navigation

Required fields: `title` (string, at most80 UTF-16 units), `state` (JSON), and
arrays `actions`, `body`, `drawer`, `menu`. `frame(...)` initializes these arrays
and `effect:null`. Flat nodes only; arbitrary nested view/layout/code objects
are not supported.

| Optional field | Meaning |
| --- | --- |
| `effect` | One service request, or null. |
| `scroll_body:true` | Scroll long form bodies; multiline editors get bounded height. Ignored with web/canvas bodies. |
| `drawer_open`, `drawer_title`, `drawer_close` | Sliding drawer and its close-action ID. |
| `drawer_width`, `drawer_max_width` | Bounded width hints; host clamps them. |
| `menu_open`, `menu_title`, `menu_close` | Centered modal containing `menu` nodes. |
| `confirm` | Object with nonempty `title`, `text`, `accept`, `cancel`; optional `accept_label`. |
| `back_action` | Action ID for app-managed Back. At most96 bytes. |
| `close_requested:true` | Request closure through the host. |
| `scroll_key`, `drawer_scroll_key` | Up to512 UTF-16 units, no control characters. At most32 retained scroll positions per model; not durable navigation state. |
| `counter`, `header_loading` | Compact header hints. |
| `full_bleed`, `hide_header_landscape` | Content hint; all full app pages retain host chrome. `hide_header_landscape` is no longer honored. |
| `presentation:"panel"` | Built-in panel hint; external app roots use the standard app page. |

Full pages/drawers slide; modal dialogs/panels use centered subtle motion. Colors,
fonts, touch targets, inset handling and much layout come from the user's OnKlav
appearance. Apps cannot replace the host chrome or install arbitrary native UI.

## Widgets

Every node requires `kind` and a unique nonempty `id` of at most96 bytes.
Action identities must be unique across the frame, including trailing actions,
copy/delete/retry/long actions. Unless stated otherwise, `text` is the label and
`enabled:false` disables interaction. Host validation and native authorization
still apply even if a node sets `enabled:true`.

| Kind | Fields and behavior |
| --- | --- |
| `label` | `text`; optional `font` (host clamps12–28sp), `strong`, `center`, `selectable`. |
| `button` | `text`; optional `primary`, `center`, `tone:"positive"`. Tap sends its ID. |
| `field` | `value`, `hint`, `limit` (0–100000 UTF-16 units); single line, optional `submit` action and `reject_overflow:true`. |
| `editor` | Same data fields, multiline editor. Prefer one editor per screen. |
| `record` | `text`, optional `detail`, `strong`, `leading_icon`, `trailing:{id,text,icon,selected}`. Tap sends ID; optional `long_action`/`long_label`. |
| `toggle` | `text`, `checked` boolean; optional `neutral`. Native checkbox/row sends boolean string. |
| `select` | `text`, `value`, `options:[{value,text}]` (maximum20). Native dropdown. |
| `chips` | `value`, same options; horizontally scrolling choices. |
| `icon` | `text` accessible label, built-in `icon` name; optional badge/selected/enabled and `long_action`/`long_label`. |
| `speech` | `value` up to50000 UTF-16 units; native speech playback/seek controls. Requires `speech.output`. |
| `speech_settings` | Native host-bundled voice/rate/preview controls; `speech.output`. |
| `web` | HTTPS `url`,32-hex-character `tab`, `on_change`, nonnegative `seq`, `command`; optional `javascript`, `find`, `on_notice`, `epoch`, `provider:"youtube"`. One body web node. See below. |
| `media_record` | Supported YouTube page `url`, matching `video` ID, unsigned `time` in milliseconds; optional `copy`, `delete`, `long_action`. Provider-specific, not arbitrary media playback. |
| `canvas` | `document:{id,revision}` or null, `tool`, ARGB `color`, `width`1–12, `on_change`; optional tool/undo/redo action IDs. One body canvas. |
| `image_record` | `document`32-character drawing ID; host retrieves authorized drawing thumbnail. |
| `slider` | `text`, integer `value`1–12. This is currently the drawing-width control, not a general arbitrary-range slider. |
| `palette` | Unsigned32-bit ARGB `value`; host's supported drawing colors. |
| `voice_dial` | Body-only offline dial; `phase:"offline"`, `enabled:false`. Other dial states are not a public implementation. |
| `voice_capture` | Body-only `peer` (maximum64 bytes), `on_change`, optional live-session hints. Requires private storage, audio grants and native microphone permission. |
| `voice_clip` | `clip`32-character ID; host-owned bounded playback/retry/delete controls. Live scope/grants checked. |

Adjacent `button`, `field`, `icon` and `toggle` nodes may share a nonempty `row`
string of at most32 bytes. This groups a flat row; it does not permit arbitrary
nesting. Small screens may scroll controls rather than reduce touch targets.

Built-in icon names: `tabs`, `contacts`, `folder`, `file`, `home`, `forward`,
`back`, `go`, `bookmark`, `menu`, `refresh`, `history`, `settings`, `rss`, `add`,
`download`, `edit`. Unknown names use the host's generic more icon. In-app icons use these stock symbols. Desktop icons additionally support
bounded signed PNG data as described above.

## Effects and replies

```rust
use onklav_app::effect;
f["effect"] = effect("records.save", json!({"note": state["doc"]}), "saved");
```

An effect is `{service,args,then}`. `then` receives a new event with
`reply:{ok:true,value:...}` or `reply:{ok:false,error:"..."}`. Preserve drafts on
failure. Do not treat a pending request as success. Only one host request can be
pending in a session. Permission changes, updates and removal invalidate sessions
and pending tokens. OS-service tokens are host-owned; guest code never receives
an Android Context, raw content URI or file handle.

### Private documents — complete base contract

Declare and obtain `storage.private`. A document is
`{id:<32 lowercase hex>,title:<string>,text:<string>,updated:<milliseconds>}`.
Use IDs returned by the service. Do not forge IDs or timestamps to cross scopes.

| Service | Args | Reply value |
| --- | --- | --- |
| `records.current` | `{}` | Last document, or a new empty document with a usable ID. |
| `records.new` | `{}` | New empty document; not saved until Save. |
| `records.load` | `{id}` | Existing document or error. |
| `records.save` | `{note:<document>}` | Saved document with host-updated timestamp. |
| `records.list` | `{query?:string}` | At most40 metadata rows `{id,title,preview,updated}`. |
| `records.page` | `{query?:string,offset?:integer}` | `{items:[metadata],total,offset}`; at most40 per page. |
| `records.delete` | `{id}` | `true`; deletes that external app's private document. Built-in Notes uses its shared-content Trash adapter. |

Each external app has its own document scope, including when two apps have the
same publisher. Scope is derived from the authenticated package ID, not args.
Private title/body limits are80/100000 UTF-16 units, quota16MB and2000 documents.

### OS effects

| Service | Capability and args |
| --- | --- |
| `clipboard.read` | `clipboard.read`; `{}`. Reply is plain text. |
| `clipboard.write` | `clipboard.write`; `{text}`. Native action reply indicates completion. |
| `files.export` | `files.export`; `{name,text}`. User-selected native destination. |
| `share.text` | `share.text`; `{name?,text,inline?:true}`. Android chooser; no silent send. |
| `contacts.pick` | `contacts.pick`; `{}`. Reply `{name,phone}` for the selected row only. Cancellation/revocation is an error. |
| `links.external` | `links.external`; `{url:<HTTPS>,target?:"youtube"}`. Explicit external open. No arbitrary target package names. |
| `web.open` | `network.web`; `{url:<HTTPS>}`. Host-controlled publisher viewer/handoff. |

These operations require a direct authorized user action. Clipboard checks also
apply to native text-selection menus. Startup and background/callback events
cannot authorize protected effects. Desktop pending replies do not establish a
successful Android action.

## Advanced development-preview components

These are implemented service families with constrained host components, not
unrestricted native APIs. Detailed domain operations/examples are in the linked
implementation guides and real controllers. Use those together with this base
contract; payloads outside the documented host model are unsupported.

| Area | Grants and current contract | Example/reference |
| --- | --- | --- |
| Feeds | `storage.private`; `network.feeds` for `feeds.fetch`. `feeds.state/query/catalog/article/bookmark/follow/edit/remove/settings/clear_cache/reset_read/reset` operate in app scope. Fetch uses host-approved HTTPS and verified commits; no raw sockets. | [RSS guide](../../docs/RSS-PORT.md), `sdk/apps/rss` |
| Browser | `storage.private` for `browser.*` tab/bookmark/settings operations. Every `web` widget needs `network.web`, `clipboard.read`, `clipboard.write`; isolated WebView provider profile required. | [Browser guide](../../docs/BROWSER-PORT.md), `sdk/apps/browser` |
| YouTube | App-scoped `videos.state/visit/bookmark/query/remove/clear_history/reset`; provider-constrained web/media records. `network.thumbnails` permits derived bounded thumbnails only. | [YouTube guide](../../docs/YOUTUBE-PORT.md), `sdk/apps/youtube` |
| Speech | `speech.output` plus visible native speech widgets. Host-bundled shared Nano engine/voices, bounded chunks, rate/preview. No network synthesis import. | [Read Aloud guide](../../docs/READER-PORT.md), `sdk/apps/reader` |
| Drawing | `storage.private` and `images.edit`; `drawing.current/list/load/rename/edit/text/undo/redo/delete`, one validated canvas. `images.import` additionally needs `files.images`; `images.export` needs destination's export/share/clipboard grant. Base image/full undo data stay host-owned. | [Annotation guide](../../docs/ANNOTATION-PORT.md), `sdk/apps/annotation` |
| Shared Files | `files.manage` for `library.list/info/folder/rename/move/copy/trash/restore/purge/import/open/export`; opaque IDs only. `files.external` for host-mediated Android storage (with legacy document-tree compatibility). Export also requires `files.export`. Mutations require direct action. | [Files guide](FILES.md), `sdk/apps/files` |
| Voice/Walkie | `storage.private`, `audio.record`, `audio.playback`; `contacts.pick` and `communications.walkie` separately. `walkie.*` private session/clip/recipient state and native voice widgets. Cellular invitations require explicit handset trial/setup; transport is not device-qualified. | [Walkie guide](../../docs/WALKIE-PORT.md), `sdk/apps/walkie` |

`web.command` accepts `load`, `back`, `forward`, `reload`, `find`, `find.next`,
`find.previous`, `find.close`, `clear`, `reset`. No guest-provided JavaScript is
executed and no native JavaScript bridge is exposed. Provider callbacks carry
host-derived URL/title/navigation and are non-gesture events. A canvas tool is
`move`, `pen`, `highlight`, `text` or `erase`; colors are unsigned32-bit ARGB,
width1–12, document ID/revision are checked before edits/exports. Do not fabricate
host-only callbacks or use a phone number/URL as a private document ID.

## All recognized capabilities

`storage.private`, `clipboard.read`, `clipboard.write`, `files.export`,
`share.text`, `network.feeds`, `network.web`, `speech.output`, `links.external`,
`network.thumbnails`, `files.images`, `images.edit`, `audio.record`,
`audio.playback`, `contacts.pick`, `communications.walkie`, `files.manage`,
`files.external`. A declaration requests a grant; it does not grant access.
External apps begin with none. Android permissions remain a separate host gate.

## Limits and failure handling

| Resource | Limit |
| --- | --- |
| Compressed app package |700000 bytes |
| Wasm / signed manifest |512000 /16384 bytes |
| Signature |64 bytes |
| Guest linear memory / stack build flag |8MiB /512KiB |
| Fuel per invocation |120000000 interpreter units |
| JSON input or output |1000000 bytes each |
| Serialized transient state |450000 bytes |
| UI nodes / combined UI text |100 /150000 UTF-16 units |
| Service continuations per event |6 |
| Live sessions per host root |8 |
| Registered app IDs, including removed/pinned IDs |100 |
| Private documents |16MB /2000 documents per app |

CPU loops, memory growth, invalid pointers, bad frames and unsupported imports
fail with an error; they do not authorize additional capabilities. A denied
service normally reaches the continuation as `reply.ok:false`; invalid UI/programs
can fail the entire event. Keep effects small, preserve drafts, handle permission
loss, and avoid rerunning failed OS actions automatically. Signing continuity
and host isolation checks are implemented; independent security qualification
and real-device acceptance remain separate requirements.

## Shared SDK0.4 library

[COMPONENTS.md](COMPONENTS.md) documents the `onklav-ui` builders/controllers,
forms, lists, routes and file/media wrappers. Included packages link the same
public library, rather than a second source copy. Header overflow, secondary
actions, form scrolling and enabled-state behavior are shared across all apps.
`catalog QUERY` discovers APIs and permissions. See [cookbook](COOKBOOK.md).

`app.onklav.` is reserved; use your own reverse-domain ID. External dialogs and
drawers have native cancellation and protected Close remains accessible. Working-source external `communications.walkie` routing still requires carrier
trial consent, host roles and grants, and two-phone qualification; declaring it
does not establish verified carrier service. Private document adapters for existing built-in data,
original-data adoption and privileged message handoffs remain host policies.

## Implemented preview account, task and playback APIs

The current reference host implements `accounts.connect` and `background.tasks` as preview capabilities; Android/provider qualification and live grants remain required. See [ACCOUNTS.md](ACCOUNTS.md) for PKCE, protected handles, refresh,
sign-out and authenticated requests, and [JOBS-MEDIA.md](JOBS-MEDIA.md) for durable
GET/download work, constraints, quotas and supported playback formats.

The body-only `media_player` widget supplies themed progress, queue, seek, speed
and video controls for the current app-owned player. Add it with
`media::player("playback")`; start a queue through a tap-triggered `media.play`
effect. It requires `audio.playback` and does not grant filesystem/network access.
Its controls cannot operate another app's player. Background work runs host
handlers; no background Wasm entry point or arbitrary worker code is enabled.

Working clock service: `clock.read` via `onklav_ui::clock::read`; see [CLOCK.md](CLOCK.md).

Working calendar components: `calendar::import_events`, `calendar::export_events`, `calendar_ui::agenda` and `calendar_ui::event_editor`; see [CALENDAR.md](CALENDAR.md).

Working `calendar.expand`, `notifications.schedule_at`, `notifications.status` and `notifications.pending` APIs are described in [RECURRENCE-REMINDERS.md](RECURRENCE-REMINDERS.md).

## Shared read-only text screen (working source)

`reading_ui::reader(title,input,content_id,revision,text)` provides shared Unicode
pages, literal search/result navigation and revision-checked page-position saving.
It uses `storage.private` through the existing collection broker. See
[Reading UI](READING-UI.md) and the runnable `developer/examples/text-reader` source.
It does not parse EPUB/PDF or update the bundled editable Read Aloud application.

## EPUB text profile (working source)

`epub::{info,chapter,search}` reads an owned immutable private EPUB blob.
`epub_ui::reader` composes TOC, chapter navigation, whole-book literal search,
fragment jumps and saved positions with the shared native text widgets. Requires
`storage.private`. See [EPUB profile and limits](EPUB.md); this is offline text
reflow, not full EPUB visual conformance. No APK or bundled app update is implied.

## Shared PDF viewer (working source)

`pdf_viewer` is a single body-only, non-scrolling-frame widget. Use
`pdf::viewer(widget_id,private_blob_id)` or `pdf::reader(title,input,private_blob_id)`.
Requires `storage.private`; copying additionally needs `clipboard.write`.
It renders the owned PDF in an isolated Android service. See [PDF limits](PDF.md).

### `map_viewer` (working source)

One bounded non-scrolling body widget renders app-private256×256 PNG/JPEG XYZ
tiles, native pan/raster zoom, optional dataset viewport callbacks, markers and visible provider attribution. Requires
`storage.private`. Provider configuration never grants network or location access.
See [Maps](MAPS.md) for the bounds, data availability and qualification limits.

### Map data adapters (working source)

`maps.cache.lookup`, `maps.cache.viewport`, `maps.cache.store` and `maps.cache.clear` require
`storage.private`. Binary `http.request` uses `response: "hex"` (up to256KiB);
text remains the default128KiB mode. HTTPS origins stay signed/revocable and
redirects remain disabled. `maps_geo` provides configured geocoding and route
adapters; see [Maps](MAPS.md) and [Geocoding/routing](MAPS-GEO.md).

### Podcast/radio adapters

`podcasts.parse` requires `storage.private` and projects bounded RSS2 audio
enclosures. `podcasts` and `radio` helpers reuse signed HTTP and shared media.
See [Podcast/radio](PODCASTS-RADIO.md).

### Read-only mail sync

`email` uses host-managed accounts and signed authenticated HTTPS for bounded
JMAP header pages and revision-checked private snapshots. See [Email](EMAIL.md).

### Mail content and compose

`email_content` provides bounded server-decoded plain text/attachment metadata;
`email_compose` provides local drafts and explicit confirmed JMAP submission.
`email_session`, `email_transfer` and `email_recovery` add configured Session
discovery, private MIME import/upload and explicit reconciliation/Sent movement.
`mail.mime.parse` and `mail.attempt.new` require `storage.private`. HTTP `upload`
is an opaque immutable private file ID, POST only, at most10MiB, no text body;
it additionally requires `storage.private` and live owner/account authorization.
See [content/compose profile](EMAIL-CONTENT.md) for exact bounds and recovery limits.

### Weather

`weather` provides configured current model conditions, canonical metric data,
metric/imperial display, attribution and an explicitly stale private-cache UI.
See [Weather](WEATHER.md).

### Service reference controllers

`reference_apps::{weather,listening,tile_map,mail}` provide source-only practical
flows through public APIs. See [reference apps](SERVICE-REFERENCE-APPS.md).

### Public radio (host79+)

`radio.stations` permits the host-owned free public station directory and explicitly selected HTTPS MP3/AAC radio streams. `radio.search` accepts bounded query/tag/country/language/page filters; `radio.play` accepts only a directory station UUID and also requires `audio.playback`. There are no credentials, API keys or arbitrary URLs. The host resolves the selected record and bounded public redirects. Opaque owner-bound in-memory stream handles are checked on every read; ordinary signed-origin HTTP remains unchanged. `media.control` sleep takes minutes0–120 and stops only the current player generation.

### Public Podcasts (host80+)

`podcasts.library` enables `podcasts.search` (bundled offline starter directory),
`podcasts.feed` (public HTTPS RSS2 episode pages), and `podcasts.play` (an episode
ID from the selected feed, with `audio.playback` and a direct tap). Public feed
URLs are not a general HTTP or account capability. No credentials or guest
stream URLs are accepted. Owner-bound opaque media leases are reauthorized on
every read. The old `podcasts.parse` adapter is preserved.

Bundled first-party apps receive their declared OnKlav capabilities by default,
including a one-time upgrade migration. Later user revocations remain effective.
External apps retain explicit capability review. Android runtime permissions and
system consent still apply. See [Podcasts](PODCASTS.md).

## AudioBooks library / build81

`audiobooks.library` provides the scoped AudioBooks library and public LibriVox browser.
Use `media_player` with `audiobook: true` and declare `storage.private`,
`audiobooks.library` and `audio.playback`. The host owns selected imports,
private large audio files, progress, chapters, bookmarks, streaming and offline
downloads. No guest paths/URLs, credentials, API keys or app listening quota.
See [AudioBooks](AUDIOBOOKS.md) for formats, recovery and provider limits.

## eBooks / build82

`ebooks.library` provides `ebooks.search` and `ebooks.acquire` for Project
Gutenberg OPDS search and selected EPUB acquisition. No guest URLs, paths, API
keys or credentials. Declare `storage.private`, `files.external` for selected
EPUB imports, and `speech.output` for shared Read Aloud. See [eBooks](EBOOKS.md).
