# App SDK assessment — separate app and game frameworks recommended

Charles asked for a deeper comparison against the existing apps and roughly
20 more conventional applications. This supersedes the earlier tentative leaning
toward Godot/GDScript for all content. No Godot app rewrite was authorized or
started. Keep the current Kotlin/Compose application work.

## Recommendation

Keep Rust for shared core/data/package/capability services, Kotlin/Compose for
application UI and Android integration, and Godot/GDScript for games. Provide
separate app and game authoring kits over common OnKlav platform services and
package management. Only OnKlav is an APK. App/game/theme packages stay internal.

This is an engineering recommendation based on feature fit, not a benchmark or
proof that either toolkit cannot implement the other category. Godot supports
non-game applications and custom controls, but its primary focus remains games.
One editor/language is not enough reason to make every app use a game engine.

## Existing source reviewed

AppCatalog, AppsPane, app-sdk/README, DEVELOPER-CAPABILITIES, app.schema.json,
Expense Log, Garden Journal, Field Organizer and Photo Markup examples, and the
original browser/annotation integration. The current built-in catalog contains
Walkie-Talkie, Social, Browser, YouTube, Read Aloud, Notes, Annotation and RSS.
Future app names/features have not been supplied; do not invent a fixed list.

| App family | Actual requirements | Framework fit judgment |
| --- | --- | --- |
| Notes and record apps | Text entry, selection, keyboard, forms, dates, checklists, calculations, photos, tabs, saved-item search | Application toolkit and reusable OnKlav widgets |
| Browser/YouTube | Embedded web/media surfaces, page state, tabs, security and native lifecycle | Host browser component exposed through app SDK |
| Read Aloud | Documents, voice selection, playback state and native TTS integration | Application toolkit plus host services |
| RSS | Structured data, lists, search, article presentation and networking | Application toolkit plus host data/network services |
| Annotation | Custom drawing, image gestures, tools, undo/redo and export | App canvas with host image/file services; does not itself require a game engine |
| Social/Walkie-Talkie | Messaging/contact UI, notifications, permissions and background audio/network lifecycle | App UI and brokered host services |
| Games | Scene rendering, animation, input, audio and game logic | Godot/GDScript |

Compose already provides configurable text fields, lazy lists/grids and built-in
accessibility semantics. Compose Multiplatform provides a possible later path
to desktop UI; it is not added to this Android project by this assessment.
OnKlav must supply reusable tabs, drawers, dialogs, records, file/media pickers,
app-scoped storage and capability APIs so app authors do not rebuild them.
SDK work done once can simplify authoring; repeated app-specific work imposed
by the wrong toolkit is the cost to avoid. Templates must allow custom screens
and programmable behavior, beyond the old finite recipe families.

## Runtime boundary

Kotlin/Compose is a UI framework, not a sandboxed installable app runtime. The old
SDK is data-only and does not prove execution of third-party apps. Ordinary
Kotlin/DEX/native plugins inside the host can bypass a capability wrapper; merely
renaming them .onklav-a is not the required system. The new app runtime needs
restricted programmable logic, a validated UI/component boundary, private data,
identity/update rules and tested capability denial/revocation. The authoring
language-to-runtime path must be proven rather than assumed. Godot packages need
an enforced execution boundary as well. The 0.10.0 initial app proof now implements this boundary using Rust/Wasm controllers
and a generic Kotlin/Compose renderer; see sdk/README.md and APPS-PORT.md.
The broader service/widget set and external Godot execution remain incomplete.

The initial SDK proof installs and updates a genuinely programmable .onklav-a
internally, exercise text/forms plus custom UI, and prove capability denial and
revocation. Continue validating creator workflow before expanding to many apps.
Do not deliver another app APK as a substitute.

Primary references consulted:
- https://docs.godotengine.org/en/stable/tutorials/ui/creating_applications.html
- https://developer.android.com/develop/ui/compose/text/user-input
- https://developer.android.com/develop/ui/compose/lists
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://kotlinlang.org/compose-multiplatform/
- https://developer.android.com/privacy-and-security/risks/dynamic-code-loading

## Dynamic Kotlin UI candidates also checked

Cash App Redwood demonstrates a Kotlin/Compose-style UI protocol with host
widgets and dynamically updated logic, but its current README says it is no
longer under active development. Zipline runs Kotlin/JS in embedded QuickJS,
but explicitly says it offers no sandbox/process isolation and must not execute
untrusted code. Neither is adopted or presented as a ready-made security boundary.
These findings reinforce the distinction between choosing app UI tools and
proving the external app runtime. Avoid bringing in a dormant framework or a
trusted-code loader just to make package loading appear finished.

- https://github.com/cashapp/redwood
- https://github.com/cashapp/zipline

## Android runtime isolation candidate to prove

Android supports host-declared isolated services with no permissions of their
own, and API 29 bindIsolatedService can create independently named service
instances from one declaration. This is a candidate for executing app logic
without creating another APK, with UI descriptions/events and capabilities
passing through validated host IPC. It is a proposed investigation, not a
completed architecture or proof that arbitrary Kotlin/Compose code can simply
run unchanged in such a worker. Dynamic package loading, file descriptors,
component protocol, time/memory limits, update identity and full lifecycle all
need tests. The existing :engine Activity is NOT an isolated service and must
not be described as one.

- https://developer.android.com/guide/topics/manifest/service-element#isolated
- https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,int,java.lang.String,java.util.concurrent.Executor,android.content.ServiceConnection)
