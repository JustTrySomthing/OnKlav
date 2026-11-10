# Working-source UI resources and recovery

These helpers are included in the current reference host described in CURRENT-HOST.md. They do not change the signed manifest schema or grant Android permissions. Implementation availability and physical-device qualification remain separate.

## Translations

Keep original display text as the fallback. Attach resources to a node or frame:

```rust
use onklav_ui::{field,localization::translated};
let name = translated(field("name", "Name", ""), "ar", &[("hint", "الاسم")])?;
```

The host selects exact locale, parent language/script and subsequent preferred
locales, then the original string, separately for each attribute. It uses Android's
configured locale list. A missing translation never removes the original label.
Translate text/hint/label/detail/long_label, frame title/drawer_title/menu_title
and confirmation accept_label. Each nested node, option, trailing action or
confirmation can carry its own resources. At most32 locale entries per object;
strings and the rendered display have bounded lengths. Bad or oversized display
resources fall back. Resources never change IDs, routes, URLs, capabilities,
input values, stored content or pending effects. App content is not automatically
machine-translated. Existing controls retain the user's theme and font settings.

Guest screens inherit direction from the primary configured locale. Shared
padding/rows follow that direction; Back uses the same mirrored chevron. System
safe areas and protected host controls stay in effect. Drawer travel retains
OnKlav's existing right-side navigation convention. Common toolbar/recovery
strings include English fallbacks and Spanish/Arabic resources; this is not a
claim that all host pages or included apps have been translated.

## Status and bounded collections

`status::loading`, `status::error` and `status::empty` produce existing themed
nodes. Status labels announce updates politely to accessibility services. A retry
is a normal explicit button action; rendering never retries an operation.
`status::page_actions` supplies previous/next buttons and disabled boundaries,
with IDs `<id>.previous` / `<id>.next`; page size is bounded to1–40. Handle those
actions in your reducer. Use existing `records.page` / file paging and
`collection::documents` to avoid loading an unbounded collection. The100-node
frame limit remains; this is not a new arbitrary virtualized layout engine.
Use `localization::translated` on these nodes for app-specific labels.

## App-state recovery contract

During ordinary recomposition/configuration changes, the ViewModel keeps the
session and dirty edits. Controls use stable IDs; an inserted earlier standalone
control does not recreate later controls. Native text input rebuilds from the
current in-memory draft when present. Text buffers are not copied into Android's
saved-instance bundle by the shared SDK field renderer.

After process termination, open a fresh authorized broker session and reload data
that the app saved using existing private-record services. The host shows a
dismissible restart notice. It stores only scalar launch-consumption markers for
this recovery path, not guest frames, input text or pending permission tokens.
Consumed launch handoffs are not replayed; a genuinely new launch key is handled
as a new user request. Normal explicit Close clears the markers. General guest
state, uncommitted edits and arbitrary external side effects are not snapshotted
or replayed. Durable application drafts must be saved explicitly; use
`RecordSchema` for versioned data and preserve existing data if decoding fails.
Android force-stop/process loss is not a promise that the last500ms of unsaved
typing reached storage. Never put credentials in normal records or resources.

The native test harness checks locale fallback, RTL bounds, large fonts, status
semantics, stored-data reload and session-token separation. Physical TalkBack,
IME/OEM and actual Android process-kill acceptance remain part of final device
qualification, not a claim established by these host checks.
