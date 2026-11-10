# OnKlav 0.100.36-rc1 / code 145

ARM64 owner-testing APK, original package and signing certificate; install as an
update. This is the fast unshrunk debug variant. Source-matched native, SDK,
speech and game outputs were reused without additional shrinking or broad tests.

Includes the completed Phone refinements since 0.100.35: taller and spaced dial
keys, theme-colored primary/row call controls, number-specific contact actions,
compact contact/recent dialogs, the matching shared OnKlav header, clear key
outlines/tinted fills shared with the Messages popup, readable accent digits,
and outlined Contacts/Recents cards. Main-menu spacing and Default Phone consent
remain intact. See PHONE-DETAIL-POLISH.md and PHONE-CHROME-KEYPAD.md.

Regular and group message feeds now share a bold vector jump-to-latest arrow,
with a stronger theme tint and contrasting black/white ink. The Activities action
and history/jump callbacks are preserved. See CHAT-LATEST-ARROW.md.

14 retained Phone/header/keypad checks and 2 new arrow checks passed; each color
check covers all 30 light/dark palette combinations. Full production/test lint
reports zero errors. Tests were not repeated during packaging. A stale generated
app R.jar was refreshed for the new shared drawable; a test-only unmerged-icon
lookup was corrected before final passing checks. No signing identity or user
state was reset.

Assembly passed in 101.17s and APK verification in 6.84s: original certificate,
package/version, ZIP integrity, 16 KB ZIP/native alignment, source-matching native
and bundled payloads, and current feature class definitions including the new
Phone details/keypad style and ChatFeedAction code. See apk36-verification.json
and docs/evidence/v0.100.36. Physical phone/carrier/OEM qualification remains
separate; this is not store certification.
