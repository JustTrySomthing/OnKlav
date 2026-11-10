# Compatibility and support

Current target: [OnKlav 0.100.42/code151, guest protocol96](CURRENT-HOST.md). Phase-specific “working/unreleased” wording below denotes preview qualification, not absence from this APK.
## Release identifiers

| Item | This release |
| --- | --- |
| Developer toolkit |0.4.1 Developer Preview |
| Reference Android host | OnKlav0.100.42-quick/code151; guest protocol96 |
| Package schema | `onklav.app.v1` |
| Interpreter/UI ABI | `rust-wasm-ui.v1` |
| Rust helper crate | `onklav-app`0.1.0 |
| Authoring language/target | Rust1.90+, `wasm32-unknown-unknown` |

The ABI/schema IDs are unchanged. Optional signed `min_host` and `icon` are now
supported by build78. Older packages without these fields remain valid. Older
hosts reject the new fields, so a package cannot silently claim a newer GUI on
an old host. New projects set `min_host:96`. This compares against the guest protocol level96, not the APK update code151. Custom PNG and stock-symbol limits
are in the API/contract. Arbitrary extension fields and Android permissions are
not supported.

Portable projects freeze the SDK dependencies under `vendor`. Existing SDK0.3
projects may use absolute live paths; moving that SDK requires updating both
Cargo dependency paths. When deliberately upgrading a linked project to0.4,
run `cargo update -p onklav-ui --manifest-path PROJECT/Cargo.toml`, inspect the
lockfile changes and run `verify PROJECT`. For portable projects, use `upgrade PROJECT`; it preserves the old vendor tree
and lockfile in a backup and restores them on failure. Review the backup/diff and
verify the app; local vendor modifications are not merged automatically. Keep app IDs, keys and private data formats stable. Structured
form schema changes may need your own migration; unreadable saved data is not
automatically overwritten.

## Surface available to outside developers

The recommended first app uses common labels, buttons, fields/editors, records,
toggles, choices, chips and built-in icons plus app-private document storage.
Clipboard, sharing and exports are supported host effects with explicit grants
and direct-user-action requirements. These paths are used by included SDK apps
and covered by runtime and host tests; actual phone interactions still matter.

Web/feed, speech, images/drawing, shared Files and voice widgets are present and
documented development-preview components. Their provider/device requirements,
additional capabilities and quotas apply to third-party packages too. A feature's
presence does not promise arbitrary native integrations: web is constrained to
supported HTTPS/profile/provider behavior, drawing follows the host document
model, and released cellular Walkie is built-in-only. Working source adds external
routing, with physical two-phone acceptance pending. Third-party apps can use local voice capture/playback. Do not market
cellular communications as ready based on the desktop harness.

Host-only operations such as installing/removing packages, granting permissions,
message-history import, original-data adoption, default-SMS setup and internal
YouTube/Walkie handoffs are **not guest effect APIs**. Some internal handoffs are
restricted to the built-in identity. App code may call only documented effects
and visible widget actions; it never gets the harness's host-control interface.

## Change rules

During the preview, components may evolve. Ship release notes naming the host
versions actually tested. The reference guide describes this snapshot; historical
porting notes are not a compatibility promise. Unknown schema/runtime IDs,
capabilities and widgets fail closed. OnKlav can reject incompatible packages;
it does not silently enable new powers. New declared capabilities require review.

Keep an app's ID and signing key forever and increase the manifest integer for
each updated package. A different binary at the same installed version is not a
valid update. Keep compatible private document formats or migrate them in app
logic after loading; the runtime does not automatically migrate your schema.
Keys are not recoverable from a package; a lost key cannot update a pinned ID.

The current runtime has no app-supplied CSS, arbitrary layout trees, custom native
views, embedded APKs or background Android services. Most UI follows the user's
OnKlav theme/font/layout settings automatically. Do not invent new manifest fields
to work around an unsupported feature. Request a reusable host API instead.

Shared GUI/controller crate: `onklav-ui`0.4.0. See [components](COMPONENTS.md).

See [capability discovery and extensions](EXTENSIONS.md) for released versus
working-source inventory, compatible libraries and native-service review.

Working source accepts an optional signed `network:{origins:[...]}` policy only
with the new `network.http` capability. Older packages omit it and remain valid;
older hosts reject the new capability/field. The current APK151/protocol96 snapshot implements this as a preview API with explicit grants and provider requirements. See [HTTPS APIs](HTTP.md).
