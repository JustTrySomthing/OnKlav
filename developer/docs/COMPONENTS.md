# Shared components — SDK0.4

Use `onklav-ui` from `developer/components` instead of writing platform UI plumbing.
The generated project links it and the low-level `onklav-app` helper automatically.
These are Rust/Wasm libraries; they do not grant access to Android APIs.

## Standard app structure

External apps on the current host have a rounded X dismissal button, app title,
up to two visible app actions and App controls. Additional actions go in the
host overflow menu, with Close App first. The app cannot hide the toolbar,
cover system areas or refuse host Close. Unsaved changes require confirmation
before this forced close. The content uses the user's colors, fonts and safe
system/keyboard insets. Never draw another OS toolbar or hardcode theme colors.

`Screen::new(title,state)` creates a screen. Chain `body`, `actions`, `effect`,
`back`, `scroll_key`, `drawer`, `dialog`, `confirm`, or `close`, then `finish`.
The host renders drawers/pages with slide navigation and dialogs with a subtle
centered scale/fade. External dialogs and drawers have native cancellation even
when guest code ignores the close event. Handle those events anyway to keep your
state consistent. A new user action can show a new modal.

```rust
use onklav_ui::{Screen, button, field, icon, serde_json::{json, Value}};
fn app(input: Value) -> Value {
    Screen::new("My App", input["state"].clone())
        .actions(vec![icon("settings", "Settings", "settings")])
        .body(vec![field("name", "Name", ""), button("save", "Save")])
        .finish()
}
fn step(input: Value) -> Value {
    onklav_ui::files::with_picker("My App", input, app)
}
onklav_ui::app!(step);
```

This example shows composition, not persistence. Your reducer must process field
changes and Save. Do not recreate native dialogs, file lists or media players.
All IDs within one frame, including hidden menu/drawer/actions, must be unique.
The `sdk.files.` prefix is reserved by the file wrapper. Treat `sdk.render` as a
no-op redraw: it must not trigger storage/network effects or navigation.

## Files and shared media

`files::with_picker(title,input,app)` adds the standard folder action, browser
with search, Up, pagination, cancellation and error UI. It preserves app state
under `state.app`; your reducer still receives its own unwrapped state. Selection
arrives as `file.selected`, with opaque metadata in `event.value`. No raw URI or
host filesystem path is exposed. Declare `files.manage`, and handle denied grants.
The chooser browses OnKlav files; it excludes System, Trash and connected Android
roots. Use `files::manager` or the `files` template for the full included Files UI,
including Android folder connections and operations with the appropriate grants.

`files::with_viewer` uses the same chooser, then the platform's existing image,
video and audio viewer through `library.open`. Results arrive as `file.opened`;
errors appear in a themed dialog. Apps do not need their own media player.
`files::open(opaque_id,then)` offers the same service for an existing selection.
Text/Notes opening uses supported host handoffs; it is not an arbitrary file-byte
API. The host closes shared media when app grants or installation change.

## Widgets and services

| Need | Reusable surface |
| --- | --- |
| Text, forms and actions | `label`, `heading`, `field`, `editor`, `button`, `primary_button`, `disabled`, `record`, `icon` |
| Preferences and choices | `toggle`, `choices("select",...)`, `choices("chips",...)`; `apps::settings` supplies private persistence and discard confirmation |
| Private editor | `apps::document` supplies New/Reload/Save, permission errors and draft discard confirmation |
| Lists/search/detail | `reference_apps::notes` supplies the included paginated document library and editor |
| Speech | `speech` and `speech_settings`; host-bundled Nano voices shared by all apps, capability `speech.output` |
| Web and feeds | `web` plus Browser/RSS/YouTube reference controllers; supported HTTPS/provider and permission rules apply |
| Drawing/images | `canvas`, `image_record`, `slider`, `palette`; Annotation controller handles document/history/tool behavior |
| Local audio | `voice_capture`, `voice_clip`, offline `voice_dial`; native microphone permission and declared audio/private-storage grants apply |
| YouTube history | `media_record`; provider-specific, not the generic audio/video viewer |
| Storage/clipboard/contact/export/share | `services` helpers; all requests go through the live permission broker |

Builders create existing supported nodes, not new widget types. More advanced
properties can be set on the returned JSON node using the [API](API.md).
`web` requires a valid app-owned tab ID and callbacks. Drawing/voice media IDs
must come from the broker, never invented paths. See the full reference
controllers for lifecycle, history and permission/error handling.

## Controllers and project templates

`new --template NAME` supports `document`, `list`, `form`, `settings`, `files`, `viewer`, `notes`,
`rss`, `reader`, `browser`, `youtube`, `annotation`, `social` and `walkie`.
The nine `reference_apps` modules are the included apps' actual implementation,
with public `controller(title,input)` entry points. Included packages are thin
wrappers linking this same crate; there is no second copied implementation. These are editable examples and
reusable controllers; do not copy a built-in ID or publisher. Social remains the
existing Hello World placeholder. Templates copy capability declarations only;
users still choose grants, and native Android permissions may also be required.

The document/settings scenarios test persistence. Files/viewer scenarios test
browsing and cancellation. Other reference scenarios check startup only. Add
scenarios for your app's special behavior and test native services on a phone.

## Boundaries still in place

Cellular `communications.walkie` is restricted to the built-in Walkie identity.
Third parties can reuse local voice capture/playback and UI, but cannot yet make
a fully equivalent cellular Walkie app. Safe multi-app incoming routing and
session ownership require more platform work. Original-data adoption, default-SMS
setup and internal privileged message handoffs remain host-only.

Optional signed `min_host` and `icon` metadata are now supported. Generated apps
require guest protocol96 and get a themed stock icon or bounded PNG. See the API and
quickstart for limits. Old packages remain compatible without either field.

## Declarative forms, lists and common properties

`forms::form(title,input,&fields)` supplies a saved form with1–20 `FormField`s.
Use text, multiline, toggle and choice fields; `required`, `limit` and `default`
configure app-specific behavior. Defaults must match field types/options. Text
limits are at most1000 UTF-16 units per field; large documents should use the
editor/controller instead. Required toggles must be checked. Choices have at
most20 unique meaningful values. IDs are unique ASCII letters/digits/underscore/
hyphen, at most60bytes. Invalid configuration produces a controlled error screen.

Forms persist values as JSON in an app-private document. They preserve drafts
on errors/cancel and refuse to overwrite unreadable saved data until an explicit
Reset confirmation. Reset loads defaults; it persists only after Save. Changed
field types/limits may require an app-specific migration of existing data. Keep
field IDs stable; the helper does not invent schema migrations.

`collection::documents` handles search, pages, list/detail, save/delete and draft
recovery. `navigation::Navigation` offers bounded route history for custom apps.
`Event` extracts actions and errors. `NodeExt` supplies enabled/limit/row/trailing/
long-action properties. `Screen::confirm_labeled` names the confirmation action;
`Screen::scrollable` makes long forms scroll. Controls respect disabled state;
disabled active media/web/drawing widgets show an inactive placeholder.

Header overflow and safe layout are the same for included and external full app
pages. Both primary and secondary overflow actions remain available. Built-in
Walkie may retain its trusted root-panel presentation; external apps keep the
host's standard full-page chrome and can open shared dialogs within it. Internal
content adapters and privileged carrier/handoff APIs remain explicit exceptions.

See [cookbook](COOKBOOK.md) for verified short projects and [catalog](../catalog.json)
for every widget/helper/controller. `catalog QUERY` finds entries without requiring
a developer to read the entire low-level API first.

## Working-source UI/lifecycle improvements

See [UI resources and recovery](UI-RESOURCES.md) for translated display resources,
RTL layouts, status/pagination helpers and the process-recreation contract. These
are available in the current reference snapshot; they do not imply physical-device qualification or a new Wasm ABI.
Host controls remain shared, themed and protected; no new widget kind is needed.
Carrier UI watchers/polling stop while hidden, while carrier delivery remains
host-owned. Date/time fields keep ISO values and use the Android clock preference.
