# Instructions for an AI app developer

Give an assistant the full SDK starter and the app's intended behavior. Start
with this request, filling in the app ID/name/features:

> Build an OnKlav application using this SDK. Read developer/README.md,
> developer/docs/API.md and developer/docs/COMPATIBILITY.md first. Use the project
> generator and shared onklav-ui controllers. Read developer/docs/COOKBOOK.md. My app ID is [your unique reverse-domain ID],
> name [name], and it should [behavior]. Keep the OnKlav platform unchanged.
> Implement only supported components/services, follow the user's theme, handle
> denied permissions and errors, and include scenario tests for saving/reopening.
> Use verify for disposable local checks. Build a shipping .onklav-a with my
> existing publisher key only for release, then report phone-only checks left. Never ask me to upload
> the private key, regenerate an existing identity, or disable runtime limits.

## Rules for the generated project

- Create an app package, not a standalone Android APK or host modification.
- Do not add unsupported manifest fields, imports, native views, raw sockets,
  filesystem paths or Android permission calls. Request supported capabilities.
- Store durable data through effects; do not assume transient JSON state survives.
- React to reply errors and preserve drafts. Avoid automatic retry/sending loops.
- Read developer/docs/COMPONENTS.md and reuse `onklav-ui` and its file/media
  wrappers. Keep the host toolbar/theme, and handle native modal cancellation.
- Do not copy built-in identities or claim external cellular Walkie access.
- Read developer/docs/CURRENT-HOST.md. Use guest protocol96 for min_host; APK code151 is a separate identifier. Never turn preview status into a device-success claim.
- Keep Cargo.lock and supported build flags. Include a scenario.json with real
  assertions about behavior; a successful compile alone is not acceptance.
- Reuse the private publisher key for updates, increase integer app version, and
  produce a new release filename. Never overwrite an existing release silently.
- Inspect the package with the production verifier, then test it with the actual
  broker. Do not implement a fake runtime or count simulated Android replies as
  real phone tests.
- State which advanced components/device interactions remain unverified. Do not
  promise arbitrary Android APIs or independently audited security.

The template is an example to edit, not a requirement to add unwanted features.
Remove unused sample controls when creating the requested app, while keeping
permission/error handling and meaningful persistence tests.

Prefer `new --template form` for declarative fields and `list` for collections.
Use portable dependencies, valid signed icon/minimum-host metadata, the catalog
and short cookbook projects. Included apps link the same public controllers;
do not copy their implementation into a second library. Do not hand-build OS
chrome, animations, media players or file browsers. Do not fabricate unsupported
camera, date/time, secure-vault, notification or external carrier APIs.
