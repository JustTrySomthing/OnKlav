# 0.52.0 — swipe menus, dial pad, Walkie and revised icon

Messages and Groups share SwipeConversation. Horizontal drags in either direction
reveal Block/Unblock, Archive/Restore and Delete, without changing persistent data.
Only one row is revealed at a time; selection mode disables swiping. Confirmations
precede mutations; ordinary Delete moves to recoverable Trash. Permanent deletion
uses the existing role-checked broker and retains locked messages. Group deletion
removes the saved group only, as before. Group blocking explicitly confirms that
it blocks other members in direct messages too; the profile number is excluded.
Block operations use the existing Rust blocked-number store and notification policy.

NumberEntryDialog uses centered input with theme-colored outline, inset key tiles
and themed numerals. PhoneVisualTransformation formats partial US input while the
underlying value remains dialable. Profile, Setup My Number and SDK recipient fields
use the same presentation. International numbers and existing short-code display
are preserved. Setup input is now normalized before saving.

Walkie remains signed SDK package version 9. Its declarative frame requests the
shared panel presentation. The host renderer provides centered rounded panels,
secondary actions in More, and an optional positive action tone for any SDK app.
Offline dial, verification, Quit and Auto Play/Auto Send remain in the main panel;
recording, clips, recent recipients and settings remain reachable through More.
Live recording uses the same outlined dial and a recording-time ring. Quit ends
the connection through the existing broker before requesting close. No game,
carrier protocol or native Rust changes were made.

artwork/OnKlav-Icon.svg and Android vectors recreate the supplied revised logo as
clean paths: integrated K cutting through the speech circle, with the broader
rounded purple tile. Legacy/adaptive/themed launcher entries are updated.
Android 12+ uses a solid purple system splash and a padded white glyph without the
square tile, then a 360ms glyph enlargement/fade on exit. Existing Android <12
startup remains static; exact old Inner Circle animation timing was not available.
Reference for this pass is the four supplied screenshots, not an executable old app.

Checks and APK hashes are in docs/source-verification.json and docs/apk-verification.json.
Handset checks remain necessary: gesture feel/vertical scrolling, actual block and
archive/delete behavior, Samsung launcher icon mask and cold-start splash, Walkie
layout at font sizes/rotation and live two-phone behavior. Full migration and
carrier acceptance remain unfinished; games remain deferred.
