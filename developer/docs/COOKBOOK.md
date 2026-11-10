# Small apps without platform plumbing

Working-source additions: [format helpers example](../examples/format-helpers/src/lib.rs)
and its [broker scenario](../examples/format-helpers/scenario.json) demonstrate
[text pages/bookmarks and radio playlists](READING-HELPERS.md) and
[UTC calendar export](CALENDAR-HELPERS.md). They perform local formatting only;
streaming, scheduling, EPUB/PDF and account services remain separate work.

Create a real project with `new`, your unique ID and a suitable template. The
examples below are complete app controllers; the generated manifest/Cargo files
supply the rest. Run `verify PROJECT` while developing. Add capabilities only
for services your app actually needs, and handle denial/revocation.

## A persisted task form

```rust
use onklav_ui::{forms::{form, FormField}, serde_json::Value};
fn step(input: Value) -> Value {
    form("Tasks", input, &[
        FormField::text("task", "Task").required(),
        FormField::multiline("details", "Details"),
        FormField::toggle("done", "Completed", false),
        FormField::choice("priority", "Priority", &[("normal", "Normal"), ("high", "High")]),
    ])
}
onklav_ui::app!(step);
```

The library handles first load/defaults, editing, required fields, validation,
Save, denied permissions, draft cancellation and a themed scrolling form. Your
code declares the app-specific fields. Declare `storage.private`.
[Runnable project](../examples/task-form/src/lib.rs),
[scenario](../examples/task-form/scenario.json).

## A searchable document collection

```rust
use onklav_ui::serde_json::Value;
fn step(input: Value) -> Value {
    onklav_ui::collection::documents("My Collection", input)
}
onklav_ui::app!(step);
```

This supplies search, pages, empty states, New, edit/Save, Back/draft confirmation
and confirmed deletion. Declare `storage.private`. Change the title and add your
special behavior, rather than reimplementing storage/navigation.
[Runnable project](../examples/document-list/src/lib.rs).

## A notebook with the existing media viewer

```rust
use onklav_ui::{apps, files, serde_json::Value};
fn notebook(input: Value) -> Value { apps::document("Notebook", input) }
fn step(input: Value) -> Value {
    files::with_viewer("Notebook", input, notebook)
}
onklav_ui::app!(step);
```

Declare `storage.private` and `files.manage`. The folder action and shared picker
open the native image/video/audio viewer; cancelling the browser preserves the
notebook's draft. The app never receives raw URIs. This does not grant arbitrary
file bytes or edit selected media. [Runnable project](../examples/media-notebook/src/lib.rs),
[scenario](../examples/media-notebook/scenario.json).

From the SDK root, verify any example without generating a permanent key:

```sh
python3 developer/onklav.py verify developer/examples/task-form
python3 developer/onklav.py verify developer/examples/document-list
python3 developer/onklav.py verify developer/examples/media-notebook
```

Examples link to the SDK with relative paths. For a separate app, copy only their
controller logic into a portable project created with `new`; use a new app ID.

## App-specific routes

`navigation::Navigation` serializes a bounded stack of at most16 routes. Restore
it from your state, call `go("settings")` on your navigation action, `back()` for
your Back action, and store `state()` in the next frame's state. Bind Back with
`Screen::back("app.back")`; close when Back returns false. Confirm unsaved changes
before popping your own route. This helper manages routes, not your business data.

## Custom screens and native services

Use `Screen`, the widget functions and `NodeExt` for fields, actions and common
properties. `Event::from_input` extracts action/value/reply errors. Only supported
field, button, toggle and icon nodes may share a `row`; the host wraps grouped
controls on narrow screens. Use `Screen::scrollable()` for multiple multiline
fields; do not put a web/canvas surface into an unbounded scrolling body.

`services` handles private records, clipboard, sharing/export and contacts.
`speech`/`speech_settings` use the host-bundled shared Nano engine and voices. Files wrappers
reuse native media. Full Browser/RSS/Reader/YouTube/Annotation/Files controllers
handle their richer state and callbacks. Read [components](COMPONENTS.md) and
[API](API.md) for permissions, inputs and error handling. OS callbacks are not
fresh gestures and cannot authorize a second protected action automatically.

## Things the platform keeps consistent

Never draw another system toolbar, guess status/navigation bar sizes, hardcode
UI colors, implement slide/pop animations, manufacture a content URI or install
an app from guest code. The host supplies these policies and stays dismissible.
Media, drawing colors and web content retain their content colors; host controls
follow appearance settings. Do not promise an unavailable camera, credential
vault, native calendar picker, background service or external carrier API.
